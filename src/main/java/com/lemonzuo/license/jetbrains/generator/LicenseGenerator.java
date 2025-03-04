package com.lemonzuo.license.jetbrains.generator;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.lemonzuo.license.jetbrains.constant.Constant;
import com.lemonzuo.license.jetbrains.domain.LicensePart;
import com.lemonzuo.license.jetbrains.service.CodeService;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author LemonZuo
 * @create 2024-02-20 22:15
 */
@Slf4j
public class LicenseGenerator {

    public static String generate(String licenseeName, Date effectiveDate, String... codes) throws Exception {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(Files.newInputStream(Paths.get(String.format("%s/ca.crt", Constant.PATH))));

        // 当天的23:59:59
        Date endOfToday = DateUtil.endOfDay(DateUtil.date());
        // 3年
        Date thereYearsLater = DateUtil.offset(endOfToday, DateField.YEAR, 3);
        // 有效日期默认为3年后
        effectiveDate = ObjectUtil.defaultIfNull(effectiveDate, thereYearsLater);

        // 生成licensePart
        List<String> codeList = new ArrayList<>();
        if (ArrayUtil.isNotEmpty(codes)) {
            Collections.addAll(codeList, codes);
        } else {
            CodeService codeService = SpringUtil.getBean(CodeService.class);
            codeList.addAll(codeService.getCodeList());
        }
        String licenseId = RandomUtil.randomString(RandomUtil.BASE_CHAR_NUMBER_LOWER.toUpperCase(), 10);

        LicensePart license = new LicensePart(licenseId, StrUtil.emptyToDefault(licenseeName, Constant.LICENSEE_NAME), codeList, DateUtil.formatDate(effectiveDate));

        String licensePart = JSONUtil.toJsonStr(license);
        log.info("licensePart: {}", licensePart);

        byte[] licensePartBytes = licensePart.getBytes(StandardCharsets.UTF_8);
        String licensePartBase64 = Base64.encode(licensePartBytes);

        PrivateKey privateKey = getPrivateKey();
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(privateKey);

        signature.update(licensePartBytes);
        byte[] signatureBytes = signature.sign();

        String sigResultsBase64 = Base64.encode(signatureBytes);
        // 生成activationCode
        String activationCode = StrUtil.join("-", licenseId, licensePartBase64, sigResultsBase64, Base64.encode(cert.getEncoded()));

        log.info("================== Activation code ==================");
        log.info("Activation code: {}", activationCode);
        log.info("================== Activation code ==================");
        return activationCode;
    }

    static PrivateKey getPrivateKey() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        PEMParser pemParser = new PEMParser(new FileReader(FileUtil.getAbsolutePath(String.format("%s/ca.key", Constant.PATH))));
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        Object object = pemParser.readObject();
        KeyPair kp = converter.getKeyPair((PEMKeyPair) object);
        return kp.getPrivate();
    }

}

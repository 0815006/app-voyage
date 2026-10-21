package com.voyage.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * RSA-OAEP 传输层解密工具（前端密码上行加密的逆操作）。
 * <p>
 * 传输链路：前端用 {@code app.crypto.rsa-public-key} 对密码明文做 RSA-OAEP(SHA-256) 加密，
 * 后端用本类以 {@code app.crypto.rsa-private-key} 还原明文，随后交 AesGcmCrypto 加密落库。
 * 内网固定密钥写死于 yml，不做轮换（零维护约定，见 application.yml app.crypto 注释）。
 * 与前端 node-forge 参数对齐：主摘要 SHA-256 + MGF1-SHA-256。
 * </p>
 */
@Component
public class RsaTransitCrypto {

    private static final String TRANSFORMATION = "RSA/ECB/OAEPPadding";

    /** OAEP 参数：显式指定 MGF1 摘要为 SHA-256，避免 JDK 默认 MGF1-SHA-1 导致前端密文解不开 */
    private static final OAEPParameterSpec OAEP_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final PrivateKey privateKey;
    private final String publicKeyBase64;

    public RsaTransitCrypto(@Value("${app.crypto.rsa-private-key:}") String privateKeyBase64,
                            @Value("${app.crypto.rsa-public-key:}") String publicKeyBase64) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            throw new IllegalStateException("缺少 RSA 私钥配置 app.crypto.rsa-private-key");
        }
        try {
            byte[] der = Base64.getDecoder().decode(privateKeyBase64.replaceAll("\\s", ""));
            this.privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("app.crypto.rsa-private-key 解析失败（需 PKCS#8 Base64）", e);
        }
        this.publicKeyBase64 = publicKeyBase64 == null ? "" : publicKeyBase64.replaceAll("\\s", "");
    }

    /**
     * 解密前端上行的密码密文。
     *
     * @param cipherBase64 Base64(RSA-OAEP 密文)
     * @return 明文密码
     */
    public String decrypt(String cipherBase64) {
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherBase64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC);
            return new String(cipher.doFinal(cipherBytes), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("密码传输密文解密失败", e);
        }
    }

    /** 空值透传解密：null/空白原样返回（承载「编辑留空=不改密」语义），非空则按密文解密。 */
    public String decryptOrNull(String cipherBase64) {
        if (cipherBase64 == null || cipherBase64.isBlank()) {
            return cipherBase64;
        }
        return decrypt(cipherBase64);
    }

    /** 供 /api/crypto/public-key 下发给前端做上行加密。 */
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }
}

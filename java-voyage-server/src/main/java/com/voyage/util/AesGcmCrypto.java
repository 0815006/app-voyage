package com.voyage.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具。
 * <p>
 * 依据项目规范「敏感数据：手机号、身份证等字段使用 AES-256-GCM 算法进行加解密存储」。
 * 密码以「随机 12 字节 IV + 密文」整体 Base64 存储，IV 无需单独落列。
 * 密钥由 {@code app.crypto.secret-key} 配置注入（环境变量优先，本地提供默认开发值）。
 * </p>
 */
@Component
public class AesGcmCrypto {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /** 密钥字节数组（32 字节 = AES-256）。 */
    private final byte[] keyBytes;

    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCrypto(@Value("${app.crypto.secret-key:}") String secretKey) {
        String key = (secretKey == null || secretKey.isBlank())
                ? "voyage-2026-dev-secret-key-0123456789ABCD"
                : secretKey;
        // 兼容任意长度输入，统一截断/补齐到 32 字节
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        this.keyBytes = new byte[32];
        System.arraycopy(raw, 0, this.keyBytes, 0, Math.min(raw.length, 32));
    }

    /**
     * 加密明文并返回「IV + 密文」的 Base64。
     *
     * @param plainText 明文，如数据库密码
     * @return Base64(iv + ciphertext)
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("待加密内容不能为 null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM 加密失败", e);
        }
    }

    /**
     * 解密 Base64 密文（前 12 字节为 IV）。
     *
     * @param encoded Base64(iv + ciphertext)
     * @return 明文
     */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("待解密内容不能为空");
        }
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length <= IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("密文格式非法");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[data.length - IV_LENGTH_BYTES];
            System.arraycopy(data, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-256-GCM 解密失败", e);
        }
    }

    private SecretKeySpec buildKey() {
        return new SecretKeySpec(keyBytes, "AES");
    }
}

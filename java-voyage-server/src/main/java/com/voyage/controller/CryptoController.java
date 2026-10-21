package com.voyage.controller;

import com.voyage.common.Result;
import com.voyage.util.RsaTransitCrypto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 传输层加密公开参数 Controller。
 * <p>
 * 向前端下发 RSA-OAEP 公钥（SPKI Base64）。公钥为公开值，泄露不影响安全性；
 * 前端取到后对 dbPass/ftpPass 上行加密，后端以 yml 中的配对私钥解密。
 * 前端内置拉取缓存（见 web-voyage-vue/src/utils/transitCrypto.ts），仅首次加密前请求一次。
 */
@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

    private final RsaTransitCrypto transitCrypto;

    public CryptoController(RsaTransitCrypto transitCrypto) {
        this.transitCrypto = transitCrypto;
    }

    /** 获取密码上行加密公钥（Base64 SPKI，RSA-2048 / OAEP-SHA256）。 */
    @GetMapping("/public-key")
    public Result<Map<String, String>> publicKey() {
        return Result.ok(Map.of("publicKey", transitCrypto.getPublicKeyBase64()));
    }
}

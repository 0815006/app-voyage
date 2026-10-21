package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.MetaFtpConfig;
import com.voyage.mapper.MetaFtpConfigMapper;
import com.voyage.service.MetaFtpConfigService;
import com.voyage.util.AesGcmCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FTP 配置管理 Service 实现。
 * <p>
 * 密码落库前统一 AES-256-GCM 加密（与数据库连接密码同一套 AesGcmCrypto 规范），
 * 任何查询出参不携带 ftpPass；FTP 登录需要明文时经 {@link #getDecrypted} 即时解密，
 * 明文仅存在于单次请求内存。
 * </p>
 */
@Service
@Slf4j
public class MetaFtpConfigServiceImpl
        extends ServiceImpl<MetaFtpConfigMapper, MetaFtpConfig>
        implements MetaFtpConfigService {

    private final AesGcmCrypto crypto;

    public MetaFtpConfigServiceImpl(AesGcmCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public List<MetaFtpConfig> listAll() {
        List<MetaFtpConfig> list = lambdaQuery().orderByDesc(MetaFtpConfig::getUpdateTime).list();
        // 出参脱敏：列表不回传任何密码（含密文）
        list.forEach(e -> e.setFtpPass(null));
        return list;
    }

    @Override
    @Transactional
    public MetaFtpConfig saveConfig(MetaFtpConfig config) {
        String plainPassword = config.getFtpPass();
        if (config.getId() == null) {
            // —— 新增：必须提供密码 ——
            if (plainPassword == null || plainPassword.isBlank()) {
                throw new BusinessException("新增FTP配置必须提供密码");
            }
            config.setFtpPass(crypto.encrypt(plainPassword));
            save(config);
            log.info("新增FTP配置: id={}, name={}", config.getId(), config.getName());
        } else {
            // —— 更新：留空保留原密文，改密重新加密 ——
            MetaFtpConfig existing = getById(config.getId());
            if (existing == null) {
                throw new BusinessException(404, "FTP配置不存在");
            }
            if (plainPassword == null || plainPassword.isBlank()) {
                config.setFtpPass(existing.getFtpPass());
            } else {
                config.setFtpPass(crypto.encrypt(plainPassword));
            }
            updateById(config);
            log.info("更新FTP配置: id={}, name={}", config.getId(), config.getName());
        }
        // 出参脱敏
        config.setFtpPass(null);
        return config;
    }

    @Override
    public MetaFtpConfig getDecrypted(Long id) {
        MetaFtpConfig existing = id == null ? null : getById(id);
        if (existing == null) {
            throw new BusinessException(404, "FTP配置不存在");
        }
        existing.setFtpPass(crypto.decrypt(existing.getFtpPass()));
        return existing;
    }
}

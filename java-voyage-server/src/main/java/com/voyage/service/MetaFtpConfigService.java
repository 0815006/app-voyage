package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaFtpConfig;

import java.util.List;

public interface MetaFtpConfigService extends IService<MetaFtpConfig> {

    /** 查询全部配置（脱敏：不返回 ftpPass） */
    List<MetaFtpConfig> listAll();

    /**
     * 新增或更新配置。ftpPass 明文非空时 AES-256-GCM 加密落库；
     * 编辑时 ftpPass 留空表示保留原密文。
     *
     * @param config 连接配置（ftpPass 为前端解密后的明文，可为空）
     * @return 保存后的实体（ftpPass 已脱敏）
     */
    MetaFtpConfig saveConfig(MetaFtpConfig config);

    /** 按 id 查询并 GCM 解密出明文 ftpPass（供 FTP 登录使用，仅存请求内存）。 */
    MetaFtpConfig getDecrypted(Long id);
}

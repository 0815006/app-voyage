package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.DbConnectionConfigEntity;
import com.voyage.mapper.DbConnectionConfigMapper;
import com.voyage.service.DbConnectionConfigService;
import com.voyage.util.AesGcmCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据库连接配置管理 Service 实现。
 * <p>
 * 密码落库前统一使用 AES-256-GCM 加密，任何场景禁止以明文持久化；
 * 查询列表仅返回脱敏数据（密码字段不返回），analyze/test-connection 需要明文时
 * 通过 {@link #getDecrypted} 解密后仅存在于请求内存中。
 * </p>
 */
@Slf4j
@Service
public class DbConnectionConfigServiceImpl
        extends ServiceImpl<DbConnectionConfigMapper, DbConnectionConfigEntity>
        implements DbConnectionConfigService {

    private final AesGcmCrypto crypto;

    public DbConnectionConfigServiceImpl(AesGcmCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public List<DbConnectionConfigEntity> listByEmpNo(String empNo) {
        List<DbConnectionConfigEntity> list = list(new LambdaQueryWrapper<DbConnectionConfigEntity>()
                .eq(DbConnectionConfigEntity::getEmpNo, empNo)
                .orderByDesc(DbConnectionConfigEntity::getCreateTime));
        // 列表输出脱敏：不携带密文也不携带明文
        list.forEach(e -> {
            e.setPasswordEnc(null);
            e.setPassword(null);
        });
        return list;
    }

    @Override
    @Transactional
    public DbConnectionConfigEntity saveConnection(String empNo, DbConnectionConfigEntity entity) {
        // 归属当前操作者
        entity.setEmpNo(empNo);
        // 密码字段优先取前端回传明文 password
        String plainPassword = entity.getPassword();

        if (entity.getId() == null || entity.getId().isBlank()) {
            // —— 新增 ——
            if (plainPassword == null || plainPassword.isBlank()) {
                throw new BusinessException("新增连接必须提供密码");
            }
            checkAliasUnique(empNo, entity.getAlias(), null);
            entity.setId(null); // 交给雪花主键生成
            entity.setPasswordEnc(crypto.encrypt(plainPassword));
            save(entity);
            log.info("新增数据库连接: id={}, alias={}, dialect={}, empNo={}",
                    entity.getId(), entity.getAlias(), entity.getDialect(), empNo);
        } else {
            // —— 更新 ——
            DbConnectionConfigEntity existing = getOwned(empNo, entity.getId());
            if (existing == null) {
                throw new BusinessException(404, "连接不存在或无权操作");
            }
            checkAliasUnique(empNo, entity.getAlias(), entity.getId());
            // 未改密：保留原密文；改密：重新加密
            if (plainPassword == null || plainPassword.isBlank()) {
                entity.setPasswordEnc(existing.getPasswordEnc());
            } else {
                entity.setPasswordEnc(crypto.encrypt(plainPassword));
            }
            updateById(entity);
            log.info("更新数据库连接: id={}, alias={}, empNo={}", entity.getId(), entity.getAlias(), empNo);
        }
        return entity;
    }

    @Override
    @Transactional
    public void deleteConnection(String empNo, String id) {
        DbConnectionConfigEntity existing = getOwned(empNo, id);
        if (existing == null) {
            throw new BusinessException(404, "连接不存在或无权操作");
        }
        removeById(id);
        log.info("删除数据库连接: id={}, alias={}, empNo={}", id, existing.getAlias(), empNo);
    }

    @Override
    public DbConnectionConfigEntity getDecrypted(String empNo, String id) {
        DbConnectionConfigEntity existing = getOwned(empNo, id);
        if (existing == null) {
            throw new BusinessException(404, "连接不存在或无权操作");
        }
        existing.setPassword(crypto.decrypt(existing.getPasswordEnc()));
        existing.setPasswordEnc(null);
        return existing;
    }

    // ==================== 私有方法 ====================

    private DbConnectionConfigEntity getOwned(String empNo, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<DbConnectionConfigEntity>()
                .eq(DbConnectionConfigEntity::getId, id)
                .eq(DbConnectionConfigEntity::getEmpNo, empNo));
    }

    /** 同一员工号下 alias 唯一性校验。 */
    private void checkAliasUnique(String empNo, String alias, String excludeId) {
        if (alias == null || alias.isBlank()) {
            throw new BusinessException("连接别名不能为空");
        }
        long count = count(new LambdaQueryWrapper<DbConnectionConfigEntity>()
                .eq(DbConnectionConfigEntity::getEmpNo, empNo)
                .eq(DbConnectionConfigEntity::getAlias, alias)
                .ne(excludeId != null && !excludeId.isBlank(),
                        DbConnectionConfigEntity::getId, excludeId));
        if (count > 0) {
            throw new BusinessException("连接别名【" + alias + "】已存在，请更换");
        }
    }
}

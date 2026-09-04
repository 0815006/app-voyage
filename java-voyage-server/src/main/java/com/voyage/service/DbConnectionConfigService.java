package com.voyage.service;

import com.voyage.entity.DbConnectionConfigEntity;

import java.util.List;

/**
 * 数据库连接配置 Service 接口（按操作员工号隔离）。
 */
public interface DbConnectionConfigService {

    /**
     * 查询当前操作者全部连接（按创建时间排序）。
     */
    List<DbConnectionConfigEntity> listByEmpNo(String empNo);

    /**
     * 新增或更新连接。密码非空时重新加密落库；
     * 密码为空且已存在旧记录时保留原密文（编辑未改密）。
     *
     * @param entity 连接配置（含明文密码字段，可为空）
     * @return 保存后的实体（含密文）
     */
    DbConnectionConfigEntity saveConnection(String empNo, DbConnectionConfigEntity entity);

    /**
     * 根据 ID + 归属员工号删除连接。
     */
    void deleteConnection(String empNo, String id);

    /**
     * 查询单个连接并解密出明文密码（供 analyze/test-connection 使用）。
     *
     * @param empNo 归属员工号
     * @param id    连接 ID
     * @return 解密后的连接（password 字段为明文）
     */
    DbConnectionConfigEntity getDecrypted(String empNo, String id);
}

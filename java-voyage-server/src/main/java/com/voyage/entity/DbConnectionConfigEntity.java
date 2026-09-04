package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库连接配置实体（db_connection_config）。
 * <p>
 * 数据库性能分析工作台的连接落表管理：按操作员工号(emp_no)隔离，
 * 同一员工号下 alias 唯一。密码以 AES-256-GCM 密文存于 password_enc，
 * 查询返回给前端时由 Service 解密为明文供临时使用（仅内存）。
 * </p>
 */
@Data
@TableName("db_connection_config")
public class DbConnectionConfigEntity {

    /** 主键，雪花 ID (VARCHAR 32) */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 归属操作员工号（7位，由后端从 EmpContext 注入，不接受前端指定） */
    @TableField("emp_no")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String empNo;

    /** 连接别名，如："生产主库" */
    private String alias;

    /** 方言：MYSQL / TDSQL / GAUSSDB / POSTGRESQL */
    private String dialect;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名（列名 db_name，JSON 键 dbName 与前端一致） */
    @TableField("db_name")
    @JsonProperty("dbName")
    private String dbName;

    /** 用户名（列名 user_name，JSON 键 user 与前端一致） */
    @TableField("user_name")
    @JsonProperty("user")
    private String userName;

    /** 密码 AES-256-GCM 密文（Base64），禁止明文（绝不序列化出参） */
    @TableField("password_enc")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordEnc;

    /** 明文密码（瞬态，JSON 键 password）：入参由前端回传，出参仅在需要时由 Controller 手动回填 */
    @TableField(exist = false)
    @JsonProperty("password")
    private String password;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

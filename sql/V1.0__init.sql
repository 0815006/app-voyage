-- ============================================================
-- Voyage Platform - 数据库初始化脚本 V1.0
-- 数据库: MySQL 8.4
-- 字符集: utf8mb4
-- 引擎: InnoDB
-- ============================================================

CREATE DATABASE IF NOT EXISTS voyage_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE voyage_db;

-- -----------------------------------------------------------
-- 1. 示例用户表 (voyage_user)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS voyage_user (
    id          VARCHAR(32)  NOT NULL COMMENT '主键 (雪花ID)',
    emp_no      VARCHAR(7)   NOT NULL COMMENT '7位员工号',
    user_name   VARCHAR(64)  NOT NULL COMMENT '用户姓名',
    email       VARCHAR(128) DEFAULT NULL COMMENT '电子邮箱',
    phone       VARCHAR(256) DEFAULT NULL COMMENT '手机号 (AES-256-GCM 加密存储)',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_emp_no (emp_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 示例操作日志表 (voyage_operation_log)
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS voyage_operation_log (
    id            VARCHAR(32)  NOT NULL COMMENT '主键 (雪花ID)',
    operator_no   VARCHAR(7)   NOT NULL COMMENT '操作人员工号',
    module        VARCHAR(64)  NOT NULL COMMENT '操作模块',
    action        VARCHAR(128) NOT NULL COMMENT '操作描述',
    request_uri   VARCHAR(256) DEFAULT NULL COMMENT '请求 URI',
    request_param TEXT         DEFAULT NULL COMMENT '请求参数 (JSON)',
    ip_address    VARCHAR(64)  DEFAULT NULL COMMENT '操作 IP',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_operator_no (operator_no),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -----------------------------------------------------------
-- 3. 插入默认管理员用户 (工号: 0000001)
-- -----------------------------------------------------------
INSERT INTO voyage_user (id, emp_no, user_name, email, status) VALUES
('10000000000000000000000000000001', '0000001', '系统管理员', 'admin@voyage.local', 1)
ON DUPLICATE KEY UPDATE user_name = VALUES(user_name);

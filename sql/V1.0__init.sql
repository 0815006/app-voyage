-- ============================================================
-- Voyage Platform - 数据库初始化脚本 V1.0
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- ============================================================

-- -----------------------------------------------------------
-- 通用触发器函数: 模拟 MySQL 的 ON UPDATE CURRENT_TIMESTAMP
-- 无参版本: 更新 update_time 字段
-- -----------------------------------------------------------
CREATE OR REPLACE FUNCTION set_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------
-- 1. 示例用户表 (voyage_user)
-- -----------------------------------------------------------
CREATE TABLE voyage_user (
    id          VARCHAR(32)  NOT NULL,
    emp_no      VARCHAR(7)   NOT NULL,
    user_name   VARCHAR(64)  NOT NULL,
    email       VARCHAR(128) DEFAULT NULL,
    phone       VARCHAR(256) DEFAULT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_emp_no UNIQUE (emp_no)
);

COMMENT ON TABLE voyage_user IS '用户表';
COMMENT ON COLUMN voyage_user.id IS '主键 (雪花ID)';
COMMENT ON COLUMN voyage_user.emp_no IS '7位员工号';
COMMENT ON COLUMN voyage_user.user_name IS '用户姓名';
COMMENT ON COLUMN voyage_user.email IS '电子邮箱';
COMMENT ON COLUMN voyage_user.phone IS '手机号 (AES-256-GCM 加密存储)';
COMMENT ON COLUMN voyage_user.status IS '状态: 1-启用, 0-禁用';
COMMENT ON COLUMN voyage_user.create_time IS '创建时间';
COMMENT ON COLUMN voyage_user.update_time IS '更新时间';
COMMENT ON COLUMN voyage_user.deleted IS '逻辑删除: 0-未删除, 1-已删除';

CREATE INDEX idx_voyage_user_create_time ON voyage_user (create_time);

CREATE TRIGGER trg_voyage_user_update_time
BEFORE UPDATE ON voyage_user
FOR EACH ROW EXECUTE FUNCTION set_update_time();

-- -----------------------------------------------------------
-- 2. 示例操作日志表 (voyage_operation_log)
-- -----------------------------------------------------------
CREATE TABLE voyage_operation_log (
    id            VARCHAR(32)  NOT NULL,
    operator_no   VARCHAR(7)   NOT NULL,
    module        VARCHAR(64)  NOT NULL,
    action        VARCHAR(128) NOT NULL,
    request_uri   VARCHAR(256) DEFAULT NULL,
    request_param TEXT         DEFAULT NULL,
    ip_address    VARCHAR(64)  DEFAULT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

COMMENT ON TABLE voyage_operation_log IS '操作日志表';
COMMENT ON COLUMN voyage_operation_log.id IS '主键 (雪花ID)';
COMMENT ON COLUMN voyage_operation_log.operator_no IS '操作人员工号';
COMMENT ON COLUMN voyage_operation_log.module IS '操作模块';
COMMENT ON COLUMN voyage_operation_log.action IS '操作描述';
COMMENT ON COLUMN voyage_operation_log.request_uri IS '请求 URI';
COMMENT ON COLUMN voyage_operation_log.request_param IS '请求参数 (JSON)';
COMMENT ON COLUMN voyage_operation_log.ip_address IS '操作 IP';
COMMENT ON COLUMN voyage_operation_log.create_time IS '创建时间';

CREATE INDEX idx_operation_log_operator_no ON voyage_operation_log (operator_no);
CREATE INDEX idx_operation_log_create_time ON voyage_operation_log (create_time);

-- -----------------------------------------------------------
-- 3. 插入默认管理员用户 (工号: 0000001)
-- -----------------------------------------------------------
INSERT INTO voyage_user (id, emp_no, user_name, email, status) VALUES
('10000000000000000000000000000001', '0000001', '系统管理员', 'admin@voyage.local', 1)
ON CONFLICT (emp_no) DO UPDATE SET user_name = EXCLUDED.user_name;

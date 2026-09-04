-- ============================================================
-- Voyage Platform - 数据库连接配置表 V1.4
-- 数据库: PostgreSQL 16+
-- 说明: 由 Flyway 在应用启动时自动执行，禁止手工执行
-- 设计: 数据库连接落表管理（数据库性能分析工作室）
--       · 支持方言: MYSQL / TDSQL / GAUSSDB / POSTGRESQL
--       · 按操作员工号(emp_no)隔离，各自维护自己的连接清单
--       · 同员工号下别名唯一
--       · 密码以 AES-256-GCM 密文(Base64)落库(password_enc)，禁止明文
-- ============================================================

-- 若存在旧表则重建（开发期幂等策略与既有脚本一致）
DROP TABLE IF EXISTS db_connection_config;

CREATE TABLE db_connection_config (
  id            VARCHAR(32)   NOT NULL,                 -- 主键(雪花ID)
  emp_no        VARCHAR(7)    NOT NULL,                 -- 归属操作员工号(7位)
  alias         VARCHAR(50)   NOT NULL,                 -- 连接别名，如"生产主库"
  dialect       VARCHAR(20)   NOT NULL,                 -- 方言: MYSQL/TDSQL/GAUSSDB/POSTGRESQL
  host          VARCHAR(255)  NOT NULL,                 -- 主机地址
  port          INTEGER       NOT NULL,                 -- 端口
  db_name       VARCHAR(255)  NOT NULL,                 -- 数据库名
  user_name     VARCHAR(255)  NOT NULL,                 -- 用户名
  password_enc  TEXT          NOT NULL,                 -- 密码 AES-256-GCM 密文(Base64)，禁止明文
  create_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

COMMENT ON TABLE  db_connection_config IS '数据库连接配置表（数据库性能分析）';
COMMENT ON COLUMN db_connection_config.id IS '主键，雪花ID(VARCHAR 32)';
COMMENT ON COLUMN db_connection_config.emp_no IS '归属操作员工号(7位)，按操作者隔离';
COMMENT ON COLUMN db_connection_config.alias IS '连接别名，如：生产主库';
COMMENT ON COLUMN db_connection_config.dialect IS '方言: MYSQL/TDSQL/GAUSSDB/POSTGRESQL';
COMMENT ON COLUMN db_connection_config.host IS '主机地址';
COMMENT ON COLUMN db_connection_config.port IS '端口';
COMMENT ON COLUMN db_connection_config.db_name IS '数据库名';
COMMENT ON COLUMN db_connection_config.user_name IS '用户名';
COMMENT ON COLUMN db_connection_config.password_enc IS '密码 AES-256-GCM 密文(Base64)，禁止明文';
COMMENT ON COLUMN db_connection_config.create_time IS '创建时间';
COMMENT ON COLUMN db_connection_config.update_time IS '更新时间';

-- 按操作者快速检索
CREATE INDEX idx_db_connection_emp_no ON db_connection_config (emp_no);

-- 同员工号下别名唯一
CREATE UNIQUE INDEX uk_db_connection_emp_alias ON db_connection_config (emp_no, alias);

-- 自动维护 update_time
CREATE TRIGGER trg_db_connection_config_update_time
BEFORE UPDATE ON db_connection_config
FOR EACH ROW EXECUTE FUNCTION set_update_time();

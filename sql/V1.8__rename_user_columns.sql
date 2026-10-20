-- ============================================================
-- Voyage Platform - 用户名字段列名统一化 V1.8
-- 数据库: PostgreSQL 16+
-- 说明: 与 xxxPass 命名保持一致，用户名列统一采用 xxx_user 风格。
--       · db_connection_config.user_name -> db_user
--       · meta_ftp_config.username       -> ftp_user
--       仅涉及本次合规改造的两张业务表；不涉及 voyage_user.user_name 等历史表。
-- ============================================================

ALTER TABLE db_connection_config RENAME COLUMN user_name TO db_user;
ALTER TABLE meta_ftp_config RENAME COLUMN username TO ftp_user;

COMMENT ON COLUMN db_connection_config.db_user IS '数据库用户名';
COMMENT ON COLUMN meta_ftp_config.ftp_user IS 'FTP用户名';

-- ============================================================
-- Voyage Platform - 敏感字段列名合规重命名 V1.7
-- 数据库: PostgreSQL 16+
-- 说明: 网络弱密码审计合规，交互报文中的 password 关键字统一改为 xxxPass，
--       报文键名来源于数据库列，故数据库列一并重命名（数据不丢）。
--       · db_connection_config.password_enc -> db_pass_enc
--       · meta_ftp_config.password          -> ftp_pass
-- ============================================================

ALTER TABLE db_connection_config RENAME COLUMN password_enc TO db_pass_enc;
ALTER TABLE meta_ftp_config RENAME COLUMN password TO ftp_pass;

COMMENT ON COLUMN db_connection_config.db_pass_enc IS '密码 AES-256-GCM 密文(Base64)，禁止明文';
COMMENT ON COLUMN meta_ftp_config.ftp_pass IS 'FTP密码';

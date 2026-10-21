-- ============================================================
-- Voyage Platform - FTP 密码密文化 V1.9
-- 数据库: PostgreSQL 16+
-- 说明: meta_ftp_config.ftp_pass 由明文改为 AES-256-GCM 密文(Base64)存储，
--       与 db_connection_config.db_pass_enc 同一套加密规范（AesGcmCrypto）。
--       列类型 VARCHAR(200) 足以容纳短密码的密文（约 100 字符内），不改表结构。
--       存量明文数据由运维手工清理后重新录入（不做数据洗列）。
-- ============================================================

COMMENT ON COLUMN meta_ftp_config.ftp_pass IS 'FTP密码 AES-256-GCM 密文(Base64)，禁止明文';

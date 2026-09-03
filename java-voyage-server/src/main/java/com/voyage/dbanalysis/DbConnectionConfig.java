package com.voyage.dbanalysis;

/**
 * 会话绑定的数据库连接配置（前端勾选时传入，含临时密码，仅存于请求内存）。
 */
public record DbConnectionConfig(
        String alias,      // 数据库别名，如 "生产主库"
        String dialect,    // 方言：MYSQL / TDSQL / GAUSSDB
        String host,
        int port,
        String dbName,
        String user,
        String password
) {
}
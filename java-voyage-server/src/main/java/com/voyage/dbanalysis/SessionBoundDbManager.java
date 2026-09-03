package com.voyage.dbanalysis;

import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.db.config.DbToolFactory;
import com.realapex.tool.db.dialect.DbDialect;
import com.realapex.tool.db.dialect.DbDialectFactory;
import com.realapex.tool.db.model.ExplainPlan;
import com.realapex.tool.db.model.QueryResult;
import com.realapex.tool.db.model.TableSchema;
import com.realapex.tool.db.tool.GetDbSchemaTool;
import com.realapex.tool.db.tool.ReadOnlyQueryTool;
import com.realapex.tool.db.tool.SqlExplainTool;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 会话绑定数据库管理器（会话级多库硬隔离）。
 * <p>
 * 依据 PRD「选中即允许、未选严禁访问」隔离原则：
 * <ul>
 *   <li>仅按前端勾选的连接列表建立会话级内存连接池（Map<Alias, DataSource>）；</li>
 *   <li>所有数据库工具均通过 {@link #runAuthorizedRead} 路由，target_alias 必须命中勾选列表，否则硬拦截；</li>
 *   <li>实际探查逻辑复用 ai-tool-db 的原子工具（方言适配 + SQL 只读安全沙箱）；</li>
 *   <li>实现 {@link AutoCloseable}，SSE 请求结束销毁所有临时连接池，密码随内存回收。</li>
 * </ul>
 */
@Slf4j
public final class SessionBoundDbManager implements AutoCloseable {

    /** 每个别名对应的数据源 */
    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();

    /** 每个别名对应的 SDK 数据库工具集 */
    private final Map<String, DbToolSet> toolSetMap = new ConcurrentHashMap<>();

    /**
     * 依据前端勾选列表初始化会话级连接池（模式 A：外部注入 DataSource 给 SDK 工具）。
     *
     * @param configs 前端勾选的数据库连接配置（含临时密码）
     */
    public void registerSelectedDatabases(List<DbConnectionConfig> configs) {
        for (DbConnectionConfig cfg : configs) {
            if (cfg.alias() == null || cfg.alias().isBlank()) {
                throw new IllegalArgumentException("数据库别名不能为空");
            }
            // 依据方言构建 JDBC URL 并建立会话级连接池（最多 3 个连接，防探查打爆目标库）
            String jdbcUrl = buildJdbcUrl(cfg);
            DataSource ds = buildDataSource(jdbcUrl, cfg.user(), cfg.password(), cfg.dialect());
            DbDialect dialect = DbDialectFactory.create(cfg.dialect());
            List<AgentTool<?, ?>> tools = DbToolFactory.createDbTools(ds, dialect);

            dataSourceMap.put(cfg.alias(), ds);
            toolSetMap.put(cfg.alias(), new DbToolSet(tools));
            log.info("[会话绑定] 注册授权数据库: alias={}, dialect={}, url={}", cfg.alias(), cfg.dialect(), jdbcUrl);
        }
    }

    /**
     * 强隔离只读路由：校验 target_alias 已授权，然后将任务分发给对应数据源执行。
     *
     * @param targetAlias 目标数据库别名（必须已勾选）
     * @param fn          在授权数据源上执行的操作
     * @return 执行结果
     */
    public Object runAuthorizedRead(String targetAlias, Function<DataSource, Object> fn) {
        DbToolSet set = toolSetMap.get(targetAlias);
        if (set == null) {
            throw new SecurityException("非法访问拦截：数据库【" + targetAlias
                    + "】未在当前会话的选中列表中！允许访问的库有: " + toolSetMap.keySet());
        }
        DataSource ds = dataSourceMap.get(targetAlias);
        return fn.apply(ds);
    }

    /** 获取授权别名集合（供构建 System Prompt + 前端展示）。 */
    public Set<String> getAuthorizedAliases() {
        return toolSetMap.keySet();
    }

    /** 获取指定别名的 SDK 工具集（供 AgentTool 包装执行时使用）。 */
    public DbToolSet getToolSet(String targetAlias) {
        DbToolSet set = toolSetMap.get(targetAlias);
        if (set == null) {
            throw new SecurityException("非法访问拦截：数据库【" + targetAlias
                    + "】未在当前会话的选中列表中！允许访问的库有: " + toolSetMap.keySet());
        }
        return set;
    }

    /** 获取指定别名的 Schema 探查工具。 */
    public GetDbSchemaTool getSchemaTool(String targetAlias) {
        return getToolSet(targetAlias).schemaTool;
    }

    /** 获取指定别名的 EXPLAIN 工具。 */
    public SqlExplainTool getExplainTool(String targetAlias) {
        return getToolSet(targetAlias).explainTool;
    }

    /** 获取指定别名的只读查询工具。 */
    public ReadOnlyQueryTool getQueryTool(String targetAlias) {
        return getToolSet(targetAlias).queryTool;
    }

    /**
     * 将 SDK 返回对象渲染为 Markdown，供 Agent 对比输出。
     */
    public String renderMarkdown(Object raw) {
        if (raw == null) {
            return "(无返回)";
        }
        if (raw instanceof TableSchema ts) {
            return renderTableSchema(ts);
        }
        if (raw instanceof ExplainPlan ep) {
            return renderExplainPlan(ep);
        }
        if (raw instanceof QueryResult qr) {
            return renderQueryResult(qr);
        }
        return raw.toString();
    }

    // ==================== 私有工具方法 ====================

    private String renderTableSchema(TableSchema ts) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 表结构: ").append(ts.schema()).append(".").append(ts.table());
        if (ts.comment() != null && !ts.comment().isBlank()) {
            sb.append(" (注释: ").append(ts.comment()).append(")");
        }
        sb.append('\n');

        Map<String, String> keyLabel = Map.of("PRI", "主键", "UNI", "唯一", "MUL", "索引");
        sb.append("| 字段 | 类型 | 可空 | 键 | 默认值 | 注释 |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        for (TableSchema.ColumnInfo c : ts.columns()) {
            String key = c.key() == null ? "" : keyLabel.getOrDefault(c.key().toUpperCase(), c.key());
            String def = c.defaultValue() == null ? "" : c.defaultValue();
            sb.append("| ").append(c.name()).append(" | ").append(c.type())
                    .append(" | ").append(c.nullable() ? "否" : "是")
                    .append(" | ").append(key)
                    .append(" | ").append(def)
                    .append(" | ").append(c.comment() == null ? "" : c.comment()).append(" |\n");
        }
        if (!ts.indexes().isEmpty()) {
            sb.append("\n**索引**:\n");
            for (TableSchema.IndexInfo idx : ts.indexes()) {
                sb.append("- ").append(idx.name())
                        .append(" (").append(String.join(", ", idx.columns()))
                        .append(idx.unique() ? ", UNIQUE" : "")
                        .append(")\n");
            }
        }
        if (!ts.primaryKeys().isEmpty()) {
            sb.append("\n**主键**: ").append(String.join(", ", ts.primaryKeys())).append('\n');
        }
        if (!ts.foreignKeys().isEmpty()) {
            sb.append("\n**外键**:\n");
            ts.foreignKeys().forEach(fk -> sb.append("- ").append(fk).append('\n'));
        }
        return sb.toString();
    }

    private String renderExplainPlan(ExplainPlan ep) {
        StringBuilder sb = new StringBuilder();
        sb.append("### EXPLAIN 执行计划 (").append(ep.dialect()).append(")\n");
        sb.append("| id | select_type | table | type | possible_keys | key | rows | Extra |\n");
        sb.append("| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (ExplainPlan.ExplainRow r : ep.rows()) {
            sb.append("| ").append(r.id()).append(" | ").append(r.selectType())
                    .append(" | ").append(r.table()).append(" | ").append(r.type())
                    .append(" | ").append(r.possibleKeys()).append(" | ").append(r.key())
                    .append(" | ").append(r.rows()).append(" | ").append(r.extra()).append(" |\n");
        }
        return sb.toString();
    }

    private String renderQueryResult(QueryResult qr) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 查询结果 (共 ").append(qr.rowCount()).append(" 行")
                .append(qr.truncated() ? ", 已截断" : "").append(")\n");
        sb.append("| ").append(String.join(" | ", qr.columns())).append(" |\n");
        sb.append("| ").append("--- |".repeat(qr.columns().size())).append('\n');
        for (List<String> row : qr.rows()) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 校验单个数据库连接是否可用（供连接管理弹窗"测试连接"使用）。
     * <p>尝试建立数据源并获取连接执行轻量校验查询，失败时抛出携带原因信息的异常。</p>
     *
     * @param cfg 待测试的连接配置（含临时密码，仅存请求内存）
     */
    public static void testConnection(DbConnectionConfig cfg) {
        String jdbcUrl = buildJdbcUrl(cfg);
        HikariDataSource hds = buildDataSource(jdbcUrl, cfg.user(), cfg.password(), cfg.dialect());
        try {
            try (var conn = hds.getConnection();
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                if (!rs.next()) {
                    throw new IllegalStateException("连接已建立但校验查询无返回结果");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("连接测试失败【" + cfg.alias() + "】: "
                    + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        } finally {
            hds.close();
        }
    }

    private static String buildJdbcUrl(DbConnectionConfig cfg) {
        String d = cfg.dialect() == null ? "MYSQL" : cfg.dialect().toUpperCase();
        return switch (d) {
            case "GAUSSDB", "OPENGAUSS", "POSTGRESQL" ->
                    "jdbc:postgresql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.dbName();
            default -> "jdbc:mysql://" + cfg.host() + ":" + cfg.port() + "/" + cfg.dbName()
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        };
    }

    private static HikariDataSource buildDataSource(String jdbcUrl, String user, String password, String dialect) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        // 限制连接池大小为 3，防止探查给目标库造成压力
        config.setMaximumPoolSize(3);
        config.setConnectionTimeout(8000); // 连接获取超时（含测试连接）
        config.setPoolName("db-analysis-" + Integer.toHexString(jdbcUrl.hashCode()));
        return new HikariDataSource(config);
    }

    /**
     * 销毁全部会话级连接池。由 Controller 在 SSE 请求结束（finally）统一调用。
     */
    @Override
    public void close() {
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            try {
                if (entry.getValue() instanceof HikariDataSource hds) {
                    hds.close();
                }
                log.info("[会话绑定] 销毁数据库连接池: alias={}", entry.getKey());
            } catch (Exception e) {
                log.warn("[会话绑定] 销毁连接池异常: {}", e.getMessage());
            }
        }
        dataSourceMap.clear();
        toolSetMap.clear();
    }

    /**
     * 单一别名的 SDK 数据库工具集（含 Schema / Explain / 只读查询）。
     */
    public static final class DbToolSet {
        public final GetDbSchemaTool schemaTool;
        public final SqlExplainTool explainTool;
        public final ReadOnlyQueryTool queryTool;

        public DbToolSet(List<AgentTool<?, ?>> tools) {
            this.schemaTool = (GetDbSchemaTool) tools.stream()
                    .filter(t -> t instanceof GetDbSchemaTool).findFirst().orElseThrow();
            this.explainTool = (SqlExplainTool) tools.stream()
                    .filter(t -> t instanceof SqlExplainTool).findFirst().orElseThrow();
            this.queryTool = (ReadOnlyQueryTool) tools.stream()
                    .filter(t -> t instanceof ReadOnlyQueryTool).findFirst().orElseThrow();
        }
    }
}
package com.voyage.dbanalysis;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.contract.AgentTool;

/**
 * 多库隔离工具集。
 * <p>
 * 依据 PRD「选中即允许、未选严禁访问」隔离原则，所有数据库工具均带 target_alias 参数，
 * 由 {@link SessionBoundDbManager} 做硬路由：target_alias 必须命中已勾选列表，
 * 否则抛出 SecurityException 拦截。实际探查逻辑复用 ai-tool-db 的原子工具。
 */
public final class AgentToolSet {

    private AgentToolSet() {
    }

    // ==================== 1. 获取指定授权库的表结构 ====================

    /**
     * get_db_schema：获取指定授权数据库中某张表的 Schema 与索引信息。
     */
    public static final class DbSchemaTool implements AgentTool<DbSchemaTool.Input, String> {

        private final SessionBoundDbManager manager;

        public DbSchemaTool(SessionBoundDbManager manager) {
            this.manager = manager;
        }

        public record Input(
                @ToolParam(description = "目标数据库别名，必须在当前会话已勾选列表中（硬隔离校验）", required = true)
                String targetAlias,
                @ToolParam(description = "数据库/模式名（MySQL 为 database，GaussDB 为 schema），留空表示当前库", required = false)
                String schema,
                @ToolParam(description = "表名，留空时返回库内表清单", required = false)
                String table
        ) {
        }

        @Override
        public String name() {
            return "get_db_schema";
        }

        @Override
        public String description() {
            return "获取指定授权数据库中某张表的 Schema 与索引信息（只读），target_alias 必须在当前会话已勾选列表内";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) throws Exception {
            Object raw = manager.getSchemaTool(req.targetAlias())
                    .execute(new com.realapex.tool.db.model.SchemaRequest(req.schema(), req.table()));
            return manager.renderMarkdown(raw);
        }
    }

    // ==================== 2. 获取 SQL 执行计划 ====================

    /**
     * explain_sql：在指定授权数据库上获取 SQL 的 EXPLAIN 执行计划。
     */
    public static final class ExplainTool implements AgentTool<ExplainTool.Input, String> {

        private final SessionBoundDbManager manager;

        public ExplainTool(SessionBoundDbManager manager) {
            this.manager = manager;
        }

        public record Input(
                @ToolParam(description = "目标数据库别名，必须在当前会话已勾选列表中（硬隔离校验）", required = true)
                String targetAlias,
                @ToolParam(description = "需要分析的 SELECT 查询 SQL", required = true)
                String sqlQuery
        ) {
        }

        @Override
        public String name() {
            return "explain_sql";
        }

        @Override
        public String description() {
            return "在指定授权数据库上获取 SQL 的 EXPLAIN 执行计划（只读），target_alias 必须在当前会话已勾选列表内";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) throws Exception {
            Object raw = manager.getExplainTool(req.targetAlias())
                    .execute(new com.realapex.tool.db.model.ExplainRequest(req.sqlQuery()));
            return manager.renderMarkdown(raw);
        }
    }

    // ==================== 3. 只读查询 ====================

    /**
     * readonly_query：在指定授权数据库执行只读查询（自动 LIMIT 防全表扫描）。
     */
    public static final class DbQueryTool implements AgentTool<DbQueryTool.Input, String> {

        private final SessionBoundDbManager manager;

        public DbQueryTool(SessionBoundDbManager manager) {
            this.manager = manager;
        }

        public record Input(
                @ToolParam(description = "目标数据库别名，必须在当前会话已勾选列表中（硬隔离校验）", required = true)
                String targetAlias,
                @ToolParam(description = "只读 SQL 语句（SELECT/SHOW/DESCRIBE/EXPLAIN），自动 LIMIT 防全表扫描", required = true)
                String sqlQuery,
                @ToolParam(description = "最大返回行数（可选，受全局上限约束，默认 100）", required = false)
                Integer maxRows
        ) {
        }

        @Override
        public String name() {
            return "readonly_query";
        }

        @Override
        public String description() {
            return "在指定授权数据库执行只读查询（自动 LIMIT 防全表扫描），target_alias 必须在当前会话已勾选列表内";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) throws Exception {
            Object raw = manager.getQueryTool(req.targetAlias())
                    .execute(new com.realapex.tool.db.model.QueryRequest(req.sqlQuery(), req.maxRows()));
            return manager.renderMarkdown(raw);
        }
    }
}
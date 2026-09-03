package com.voyage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voyage.common.Result;
import com.voyage.dbanalysis.DbAnalysisWorkbench;
import com.voyage.dbanalysis.DbConnectionConfig;
import com.voyage.dbanalysis.SessionBoundDbManager;
import com.voyage.entity.SessionDbAnalysis;
import com.voyage.service.SessionDbAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据库性能分析工作室 Controller。
 * <p>
 * 提供会话管理（新建/列表/删除）与 SQL 流式分析（SSE）与人工审批恢复（HITL）。
 * 依据 PRD「零密码落盘」：数据库密码由前端随请求传入，后端仅在单次请求内存中使用，
 * 会话持久化仅保存不含密码的元数据快照。
 *
 * @author voyage
 */
@Slf4j
@RestController
@RequestMapping("/api/db-analysis")
public class DbAnalysisController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DbAnalysisWorkbench workbench;
    private final SessionDbAnalysisService sessionService;

    public DbAnalysisController(DbAnalysisWorkbench workbench,
                                SessionDbAnalysisService sessionService) {
        this.workbench = workbench;
        this.sessionService = sessionService;
    }

    // ==================== 会话管理 ====================

    /** 会话列表（按更新时间倒序）。 */
    @GetMapping("/sessions")
    public Result<List<SessionDbAnalysis>> listSessions() {
        return Result.ok(sessionService.listSessions());
    }

    /** 会话详情。 */
    @GetMapping("/session/{id}")
    public Result<SessionDbAnalysis> getSession(@PathVariable String id) {
        return Result.ok(sessionService.getSession(id));
    }

    /**
     * 新建会话。
     *
     * @param body 包含 sessionTitle 与 selectedDbMeta（不含密码的元数据快照 JSON 数组）
     */
    @PostMapping("/session")
    public Result<SessionDbAnalysis> createSession(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("sessionTitle", "数据库性能分析");
        String selectedDbMeta = body.getOrDefault("selectedDbMeta", "[]");
        return Result.ok(sessionService.createSession(title, selectedDbMeta));
    }

    /** 删除会话。 */
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable String id) {
        sessionService.deleteSession(id);
        return Result.ok();
    }

    // ==================== 连接测试 ====================

    /**
     * 测试单个数据库连接是否可用（供连接管理弹窗"测试连接"按钮使用）。
     *
     * @param body 单个连接配置（含临时密码，仅存请求内存）
     */
    @PostMapping("/test-connection")
    public Result<Map<String, Object>> testConnection(@RequestBody Map<String, Object> body) {
        DbConnectionConfig cfg = parseConnection(body);
        try {
            SessionBoundDbManager.testConnection(cfg);
            return Result.ok(Map.of("success", true, "alias", cfg.alias()));
        } catch (Exception e) {
            log.warn("连接测试失败 alias={}: {}", cfg.alias(), e.getMessage());
            return Result.ok(Map.of(
                    "success", false,
                    "alias", cfg.alias(),
                    "message", e.getMessage()));
        }
    }

    // ==================== 流式分析（SSE） ====================

    /**
     * 发起一次数据库性能分析（SSE 流式）。
     *
     * @param body 包含 sessionId、prompt、active_db_connections（含临时密码）
     */
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyze(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId", "");
        String prompt = (String) body.getOrDefault("prompt", "");

        List<DbConnectionConfig> connections = parseConnections(body);
        log.info("DB分析请求, sessionId={}, promptLen={}, 授权库数={}",
                sessionId, prompt.length(), connections.size());

        return workbench.run(sessionId, prompt, connections);
    }

    /**
     * 人工审批后恢复挂起的 Agent 执行（SSE 流式）。
     *
     * @param body 包含 suspendId、approved、comment
     */
    @PostMapping(value = "/approve", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter approve(@RequestBody Map<String, Object> body) {
        String suspendId = (String) body.get("suspendId");
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String comment = body.get("comment") == null ? "" : body.get("comment").toString();
        log.info("DB分析审批, suspendId={}, approved={}, 操作人={}",
                suspendId, approved, com.voyage.common.EmpContext.getEmpNo());
        try {
            return workbench.resume(suspendId, approved, comment);
        } catch (Exception e) {
            log.error("DB分析审批恢复失败: {}", e.getMessage());
            throw e;
        }
    }

    // ==================== 内部工具 ====================

    /** 解析请求中的 active_db_connections 列表为连接配置（含临时密码，仅存请求内存）。 */
    @SuppressWarnings("unchecked")
    private List<DbConnectionConfig> parseConnections(Map<String, Object> body) {
        List<DbConnectionConfig> connections = new ArrayList<>();
        Object raw = body.get("active_db_connections");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) {
                    continue;
                }
                Map<String, Object> map = (Map<String, Object>) m;
                DbConnectionConfig cfg = parseConnection(map);
                if (cfg.alias() != null && !cfg.alias().isBlank()) {
                    connections.add(cfg);
                }
            }
        }
        return connections;
    }

    /** 解析单个连接 Map 为连接配置。 */
    @SuppressWarnings("unchecked")
    private DbConnectionConfig parseConnection(Map<String, Object> map) {
        String alias = str(map.get("alias"));
        String dialect = str(map.get("dialect"));
        String host = str(map.get("host"));
        int port = intOf(map.get("port"), 3306);
        String dbName = str(map.get("db_name"));
        String user = str(map.get("user"));
        String password = str(map.get("password"));
        return new DbConnectionConfig(alias, dialect, host, port, dbName, user, password);
    }

    /** 解析请求正文中的单个连接配置。 */
    @SuppressWarnings("unchecked")
    private DbConnectionConfig parseConnection(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            return parseConnection((Map<String, Object>) m);
        }
        return new DbConnectionConfig("", "", "", 0, "", "", "");
    }

    private String str(Object o) {
        return o == null ? null : o.toString();
    }

    private int intOf(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
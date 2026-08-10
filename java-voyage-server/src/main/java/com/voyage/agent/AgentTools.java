package com.voyage.agent;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.contract.AgentTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI Agent 工具集。
 * <p>
 * 基于 ai-tool-sdk 的 {@link AgentTool} 契约实现，供 AgentRunner 调度使用：
 * <ol>
 *   <li>query_system_log — 系统日志查询</li>
 *   <li>get_heavy_metrics — 性能指标获取（触发 ContextTrimmer 截断）</li>
 *   <li>execute_system_command — 系统命令执行（触发 SafeGuard 安全校验）</li>
 *   <li>local_file_reader — 本地文件读取</li>
 * </ol>
 */
public final class AgentTools {

    private AgentTools() { /* 工具容器，不实例化 */ }

    // ==================== 1. 系统日志查询 ====================

    /**
     * 查询指定模块的系统运行日志。
     */
    public static final class SystemLogTool implements AgentTool<SystemLogTool.Input, String> {

        public record Input(
                @ToolParam(description = "模块名称", required = true) String module,
                @ToolParam(description = "返回日志行数") int lines
        ) {}

        @Override
        public String name() {
            return "query_system_log";
        }

        @Override
        public String description() {
            return "查询指定模块的系统运行日志，返回日志列表";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) {
            return String.format("""
                    [2026-08-10 16:20:01] INFO  [%1$s] - Server started on port 8080.
                    [2026-08-10 16:20:05] WARN  [%1$s] - Slow query detected in database pool! Cost: 3450ms.
                    [2026-08-10 16:20:10] ERROR [%1$s] - NullPointerException in OrderService.java:42
                    [2026-08-10 16:20:15] INFO  [%1$s] - Health check passed, all services online.
                    [2026-08-10 16:20:20] WARN  [%1$s] - Memory usage exceeds threshold: 85%%.
                    """, req.module());
        }
    }

    // ==================== 2. 性能指标查询 ====================

    /**
     * 获取指定节点的全量性能指标数据。返回数据量较大，由 ContextTrimmer 负责截断。
     */
    public static final class HeavyMetricsTool implements AgentTool<HeavyMetricsTool.Input, String> {

        public record Input(
                @ToolParam(description = "目标节点 IP 地址", required = true) String nodeIp
        ) {}

        @Override
        public String name() {
            return "get_heavy_metrics";
        }

        @Override
        public String description() {
            return "获取指定节点的全量性能指标数据（数据量较大，超出 Token 预算时自动截断）";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) {
            StringBuilder sb = new StringBuilder("=== Metrics Start (Node: ")
                    .append(req.nodeIp()).append(") ===\n");
            for (int i = 0; i < 2000; i++) {
                sb.append("metric_cpu_usage_core_").append(i)
                        .append("=").append(String.format("%.4f", Math.random() * 100)).append("\n");
            }
            sb.append("=== Metrics End ===");
            return sb.toString();
        }
    }

    // ==================== 3. 系统命令执行 ====================

    /**
     * 在服务器上执行指定的 Shell 系统命令。
     * 高危命令会被安全沙箱（DangerousCommandFilter）拦截。
     */
    public static final class SystemCommandTool implements AgentTool<SystemCommandTool.Input, String> {

        public record Input(
                @ToolParam(description = "要执行的 Shell 系统命令", required = true) String command
        ) {}

        @Override
        public String name() {
            return "execute_system_command";
        }

        @Override
        public String description() {
            return "在服务器上执行指定的 Shell 系统命令。高危命令会被安全沙箱自动拦截。";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) {
            String cmd = req.command().toLowerCase();
            if (cmd.contains("rm ") || cmd.contains("rm\t") ||
                    cmd.contains("drop ") || cmd.contains("drop\t") ||
                    cmd.contains("delete ") || cmd.contains("truncate ") ||
                    cmd.contains("mkfs") || cmd.contains("dd if=") ||
                    cmd.contains("> /dev/") || cmd.contains("format ")) {
                throw new SecurityException(
                        "[SafeGuard] 高危命令被安全沙箱拦截: " + req.command());
            }
            return "Command executed successfully: " + req.command();
        }
    }

    // ==================== 4. 本地文件读取 ====================

    /**
     * 读取指定路径的文件内容。
     * 支持 TXT、LOG、JSON、Java、MD、CSV 等文本文件。
     */
    public static final class LocalFileReaderTool implements AgentTool<LocalFileReaderTool.Input, String> {

        public record Input(
                @ToolParam(description = "文件的绝对路径", required = true) String filePath
        ) {}

        @Override
        public String name() {
            return "local_file_reader";
        }

        @Override
        public String description() {
            return "读取指定绝对路径的文件内容（支持 TXT, LOG, JSON, Java, MD, CSV 等文本文件）";
        }

        @Override
        public Class<Input> requestClass() {
            return Input.class;
        }

        @Override
        public String execute(Input req) {
            try {
                Path path = Paths.get(req.filePath());
                if (!Files.exists(path)) {
                    return "错误: 文件不存在 — " + req.filePath();
                }
                if (!Files.isReadable(path)) {
                    return "错误: 文件不可读 — " + req.filePath();
                }
                return Files.readString(path);
            } catch (IOException e) {
                return "读取文件出错: " + e.getMessage();
            }
        }
    }
}

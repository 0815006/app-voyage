package com.voyage.agent;

import com.realapex.agent.execution.AgentRunner;
import com.realapex.client.client.AiClient;
import com.realapex.tool.schema.SchemaGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 基础设施 Bean 注册。
 * <p>
 * 显式创建 AgentRunner / SchemaGenerator / 工具 Bean，避免依赖 SDK jar 内
 * AutoConfiguration 的加载顺序问题。
 */
@Configuration
public class AgentToolsConfig {

    // ==================== 基础设施 ====================

    /**
     * AgentRunner 显式注册。
     * <p>
     * SDK 的 AgentAutoConfiguration 中 AgentRunner 带有
     * {@code @ConditionalOnBean(AiClient.class)}，在某些场景下
     * auto-config 加载顺序导致条件不满足而被跳过。
     * 这里在用户 Configuration 中显式创建，此时 AiClient 已就绪。
     */
    @Bean
    public AgentRunner agentRunner(AiClient aiClient, SchemaGenerator schemaGenerator) {
        return new AgentRunner(aiClient, schemaGenerator);
    }

    // ==================== 工具 ====================

    @Bean
    public AgentTools.SystemLogTool systemLogTool() {
        return new AgentTools.SystemLogTool();
    }

    @Bean
    public AgentTools.HeavyMetricsTool heavyMetricsTool() {
        return new AgentTools.HeavyMetricsTool();
    }

    @Bean
    public AgentTools.SystemCommandTool systemCommandTool() {
        return new AgentTools.SystemCommandTool();
    }

    @Bean
    public AgentTools.LocalFileReaderTool localFileReaderTool() {
        return new AgentTools.LocalFileReaderTool();
    }
}

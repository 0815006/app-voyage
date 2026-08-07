package com.voyage.controller;

import com.realapex.client.client.AiClient;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.Message;
import com.realapex.client.client.StreamListener;
import com.voyage.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AI 会话 Controller —— 通过 ai-client-sdk 调用大模型。
 * 提供同步和 SSE 流式两种调用方式。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiClient aiClient;

    /** SDK 自动装配 AiClient 后通过构造器注入 */
    public AiChatController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    /**
     * 同步聊天：一次性返回完整回复。
     */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        log.info("AI sync chat, prompt length: {}", prompt.length());

        String reply = aiClient.generateText(AiRequest.builder()
                .messages(List.of(Message.user(prompt)))
                .build());

        return Result.ok(Map.of("reply", reply));
    }

    /**
     * 流式聊天：SSE 逐字返回（打字机效果）。
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        log.info("AI stream chat, prompt length: {}", prompt.length());

        SseEmitter emitter = new SseEmitter(120_000L);

        Thread.startVirtualThread(() -> aiClient.streamText(
                AiRequest.builder()
                        .messages(List.of(Message.user(prompt)))
                        .build(),
                new StreamListener() {
                    @Override
                    public void onChunk(String chunk) {
                        log.debug("AI stream onChunk, length: {}", chunk.length());
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            log.error("SSE send failed", e);
                        }
                    }

                    @Override
                    public void onComplete() {
                        log.info("AI stream onComplete");
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        log.error("AI stream onError: {}", e.getMessage(), e);
                        emitter.completeWithError(e);
                    }
                }));

        return emitter;
    }
}

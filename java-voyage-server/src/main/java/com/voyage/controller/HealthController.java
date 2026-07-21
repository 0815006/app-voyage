package com.voyage.controller;

import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 健康检查与系统信息 Controller。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now().toString(),
                "operator", EmpContext.getEmpNo(),
                "java", System.getProperty("java.version")
        ));
    }
}

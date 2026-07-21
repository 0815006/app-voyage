package com.voyage.controller;

import com.voyage.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统信息 Controller —— 提供 StatusBar 所需数据。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        String loginIp = getClientIp(request);
        return Result.ok(Map.of(
                "loginIp", loginIp,
                "serverTime", LocalDateTime.now().toString()
        ));
    }

    /**
     * 获取客户端真实 IP（考虑反向代理）。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

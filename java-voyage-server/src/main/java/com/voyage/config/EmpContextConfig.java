package com.voyage.config;

import com.voyage.common.EmpContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 员工号上下文配置 —— 注册 HandlerInterceptor，自动从 X-Emp-No 请求头提取工号。
 */
@Slf4j
@Configuration
public class EmpContextConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new EmpInterceptor())
                .addPathPatterns("/api/**")
                .order(1);
    }

    private static class EmpInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String empNo = request.getHeader("X-Emp-No");
            if (empNo != null && !empNo.isBlank()) {
                EmpContext.setEmpNo(empNo.trim());
            } else {
                EmpContext.setEmpNo("0000000");
            }
            log.debug("请求 [{}] {} - 操作员: {}", request.getMethod(), request.getRequestURI(), EmpContext.getEmpNo());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                    Object handler, Exception ex) {
            EmpContext.clear();
        }
    }
}

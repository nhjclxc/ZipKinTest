package com.nhjclxc.zipkintest.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪响应过滤器
 * 在响应头中添加链路追踪信息
 */
@Slf4j
@Component
@Order(1) // 使用最高优先级，确保最先执行
public class TraceResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

        try {
            // 获取真实的链路追踪信息
            String traceId = getTraceId();
            String spanId = getSpanId();
            String requestId = generateRequestId();

            // 在请求处理之前就设置响应头
            if (traceId != null) {
                response.setHeader("X-Trace-Id", traceId);
            }
            if (spanId != null) {
                response.setHeader("X-Span-Id", spanId);
            }
            if (requestId != null) {
                response.setHeader("X-Request-Id", requestId);
            }
            if (traceId != null && spanId != null) {
                response.setHeader("X-Trace-Info", traceId + ":" + spanId);
            }

            // 继续处理请求
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("TraceResponseFilter 处理异常: {}", e.getMessage());
            // 继续处理，不影响正常请求
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 获取当前线程的traceId
     */
    private String getTraceId() {
        try {
            // 从MDC中获取traceId
            String traceId = org.slf4j.MDC.get("traceId");

            if (traceId == null || traceId.isEmpty()) {
                // 尝试其他可能的key
                traceId = org.slf4j.MDC.get("X-B3-TraceId");
            }

            if (traceId == null || traceId.isEmpty()) {
                // 如果还是没有，生成一个
                traceId = "gen-" + UUID.randomUUID().toString().substring(0, 8);
            }

            return traceId;
        } catch (Exception e) {
            return "err-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 获取当前线程的spanId
     */
    private String getSpanId() {
        try {
            // 从MDC中获取spanId
            String spanId = org.slf4j.MDC.get("spanId");

            if (spanId == null || spanId.isEmpty()) {
                // 尝试其他可能的key
                spanId = org.slf4j.MDC.get("X-B3-SpanId");
            }

            if (spanId == null || spanId.isEmpty()) {
                // 如果还是没有，生成一个
                spanId = "span-" + UUID.randomUUID().toString().substring(0, 8);
            }

            return spanId;
        } catch (Exception e) {
            return "err-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * 生成请求ID
     */
    private String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 排除静态资源和错误页面
        String path = request.getRequestURI();
        return path.startsWith("/static/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/favicon.ico") ||
               path.equals("/error");
    }
}

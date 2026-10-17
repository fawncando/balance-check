package com.bzd.balance.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        Object user = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(CurrentUser.SESSION_KEY);
        if (user != null) {
            return true;
        }
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                java.util.Map.of("code", 401, "message", "未登录或会话已过期", "data", "")));
        return false;
    }
}

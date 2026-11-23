package com.bzd.balance.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.bzd.balance.user.User;
import com.bzd.balance.user.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final UserMapper userMapper;

    public AuthInterceptor(ObjectMapper objectMapper, UserMapper userMapper) {
        this.objectMapper = objectMapper;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getMethod().equals("OPTIONS")) {
            return true;
        }
        Object value = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(CurrentUser.SESSION_KEY);
        if (value instanceof SessionUser sessionUser) {
            User current = userMapper.findById(sessionUser.getId());
            int sessionVersion = sessionUser.getSessionVersion() == null
                    ? 0 : sessionUser.getSessionVersion();
            int currentVersion = current == null || current.getSessionVersion() == null
                    ? 0 : current.getSessionVersion();
            if (current != null && "ACTIVE".equals(current.getStatus())
                    && sessionVersion == currentVersion) {
                request.getSession(false).setAttribute(
                        CurrentUser.SESSION_KEY,
                        new SessionUser(current.getId(), current.getUsername(),
                                current.getDisplayName(), current.getRole(), current.getCardNo(),
                                current.getSessionVersion()));
                return true;
            }
            request.getSession(false).invalidate();
        }
        response.setStatus(401);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                java.util.Map.of("code", 401, "message", "未登录或会话已过期", "data", "")));
        return false;
    }
}

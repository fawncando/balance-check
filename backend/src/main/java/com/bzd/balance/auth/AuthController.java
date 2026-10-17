package com.bzd.balance.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.common.ApiResponse;
import com.bzd.balance.log.AuditService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public ApiResponse<SessionUser> login(@Valid @RequestBody LoginRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpSession session) {
        SessionUser user = authService.login(request.getUsername(), request.getPassword(),
                httpRequest.getRemoteAddr());
        session.setAttribute(CurrentUser.SESSION_KEY, user);
        return ApiResponse.ok(user);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session, HttpServletRequest request) {
        Object value = session.getAttribute(CurrentUser.SESSION_KEY);
        if (value instanceof SessionUser user) {
            auditService.log(user.getId(), "LOGOUT", "USER", String.valueOf(user.getId()), "退出登录",
                    request.getRemoteAddr());
        }
        session.invalidate();
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<SessionUser> me(HttpSession session) {
        return ApiResponse.ok(CurrentUser.require(session));
    }
}

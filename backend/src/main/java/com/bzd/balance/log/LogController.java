package com.bzd.balance.log;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;
import com.bzd.balance.common.PageView;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final AuditLogService auditLogService;

    public LogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PageView<AuditLogView>> list(AuditLogQuery query, HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(auditLogService.search(query, user));
    }
}

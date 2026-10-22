package com.bzd.balance.template;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/templates")
public class RecordTemplateController {

    private final RecordTemplateService recordTemplateService;

    public RecordTemplateController(RecordTemplateService recordTemplateService) {
        this.recordTemplateService = recordTemplateService;
    }

    @GetMapping
    public ApiResponse<List<RecordTemplateView>> list(HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(recordTemplateService.list(user));
    }

    @PostMapping
    public ApiResponse<RecordTemplateView> create(@RequestBody RecordTemplateRequest request,
                                                  HttpSession session,
                                                  HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(recordTemplateService.create(request, user, http.getRemoteAddr()));
    }

    /** 启用或停用一个模板版本；启用时同名的其他版本自动停用。 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<RecordTemplateView> setEnabled(@PathVariable Long id,
                                                      @RequestParam boolean enabled,
                                                      HttpSession session,
                                                      HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(recordTemplateService.setEnabled(id, enabled, user, http.getRemoteAddr()));
    }
}

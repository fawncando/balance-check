package com.bzd.balance.user;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserCardService userCardService;
    private final UserAdminService userAdminService;

    public UserController(UserCardService userCardService,
                          UserAdminService userAdminService) {
        this.userCardService = userCardService;
        this.userAdminService = userAdminService;
    }

    /** 用户列表：操作日志按操作人筛选时使用，只对管理员开放。 */
    @GetMapping
    public ApiResponse<List<UserView>> list(HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(userCardService.list(user));
    }

    @PostMapping
    public ApiResponse<UserView> create(@RequestBody UserCreateRequest request,
                                        HttpSession session,
                                        HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(userAdminService.create(
                request, user, http.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id,
                                        @RequestBody UserUpdateRequest request,
                                        HttpSession session,
                                        HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(userAdminService.update(
                id, request, user, http.getRemoteAddr()));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @RequestBody PasswordResetRequest request,
                                           HttpSession session,
                                           HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        userAdminService.resetPassword(id, request, user, http.getRemoteAddr());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    HttpSession session,
                                    HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        userAdminService.delete(id, user, http.getRemoteAddr());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/card")
    public ApiResponse<UserView> bindCard(@PathVariable Long id,
                                          @RequestBody UserCardRequest request,
                                          HttpSession session,
                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(userCardService.updateCard(
                id, request, user, http.getRemoteAddr()));
    }

    @DeleteMapping("/{id}/card")
    public ApiResponse<UserView> clearCard(@PathVariable Long id,
                                           HttpSession session,
                                           HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(userCardService.clearCard(
                id, user, http.getRemoteAddr()));
    }

    @GetMapping(value = "/{id}/card/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> cardQr(@PathVariable Long id, HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(userCardService.cardQr(id, user));
    }
}

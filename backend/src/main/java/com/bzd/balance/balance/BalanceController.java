package com.bzd.balance.balance;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceService balanceService;
    private final BalanceMaintenanceService balanceMaintenanceService;

    public BalanceController(BalanceService balanceService,
                             BalanceMaintenanceService balanceMaintenanceService) {
        this.balanceService = balanceService;
        this.balanceMaintenanceService = balanceMaintenanceService;
    }

    @GetMapping
    public ApiResponse<List<BalanceView>> list() {
        return ApiResponse.ok(balanceService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<BalanceView> detail(@PathVariable Long id) {
        return ApiResponse.ok(balanceService.get(id));
    }

    /** 新增天平：基本信息与量程一起提交。 */
    @PostMapping
    public ApiResponse<BalanceView> create(@RequestBody BalanceRequest request,
                                           HttpSession session,
                                           HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(balanceMaintenanceService.create(request, user, http.getRemoteAddr()));
    }

    /** 修改天平：量程整体替换，状态改为 INACTIVE 即为停用。 */
    @PutMapping("/{id}")
    public ApiResponse<BalanceView> update(@PathVariable Long id,
                                           @RequestBody BalanceRequest request,
                                           HttpSession session,
                                           HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(balanceMaintenanceService.update(id, request, user, http.getRemoteAddr()));
    }
}

package com.bzd.balance.balance;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.common.ApiResponse;

@RestController
@RequestMapping("/api/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public ApiResponse<List<BalanceView>> list() {
        return ApiResponse.ok(balanceService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<BalanceView> detail(@PathVariable Long id) {
        return ApiResponse.ok(balanceService.get(id));
    }
}

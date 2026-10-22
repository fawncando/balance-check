package com.bzd.balance.balance;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.common.ApiResponse;

@RestController
@RequestMapping("/api/weight-sets")
public class WeightSetController {

    private final WeightSetService weightSetService;

    public WeightSetController(WeightSetService weightSetService) {
        this.weightSetService = weightSetService;
    }

    @GetMapping
    public ApiResponse<List<WeightSetView>> list() {
        return ApiResponse.ok(weightSetService.listAll());
    }
}

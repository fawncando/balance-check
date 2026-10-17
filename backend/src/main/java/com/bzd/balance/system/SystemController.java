package com.bzd.balance.system;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.common.ApiResponse;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final StorageService storageService;

    public SystemController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "balance-check");
        body.put("version", "0.1.0");
        body.put("time", LocalDateTime.now());
        body.put("storage", storageService.stats());
        return ApiResponse.ok(body);
    }
}

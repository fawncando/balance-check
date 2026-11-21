package com.bzd.balance.system;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.common.ApiResponse;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final StorageService storageService;
    private final MobileEntryService mobileEntryService;

    public SystemController(StorageService storageService,
                            MobileEntryService mobileEntryService) {
        this.storageService = storageService;
        this.mobileEntryService = mobileEntryService;
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

    @GetMapping("/mobile-entry")
    public ApiResponse<Map<String, Object>> mobileEntry() {
        return ApiResponse.ok(mobileEntryService.entry());
    }

    @GetMapping(value = "/mobile-entry/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> mobileEntryQr() {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(mobileEntryService.qrPng());
    }
}

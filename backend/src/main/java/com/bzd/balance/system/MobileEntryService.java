package com.bzd.balance.system;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MobileEntryService {

    private final String configuredPublicUrl;
    private final int mobilePort;
    private final QrCodeService qrCodeService;

    public MobileEntryService(
            @Value("${app.public-url:}") String configuredPublicUrl,
            @Value("${app.mobile-port:5173}") int mobilePort,
            QrCodeService qrCodeService) {
        this.configuredPublicUrl = configuredPublicUrl;
        this.mobilePort = mobilePort;
        this.qrCodeService = qrCodeService;
    }

    public Map<String, Object> entry() {
        String url = resolvedPublicUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("path", "/mobile");
        body.put("lanIp", lanAddress());
        return body;
    }

    public byte[] qrPng() {
        return qrCodeService.png(resolvedPublicUrl());
    }

    private String resolvedPublicUrl() {
        if (configuredPublicUrl != null && !configuredPublicUrl.isBlank()) {
            return configuredPublicUrl.replaceAll("/+$", "") + "/mobile";
        }
        return "http://" + lanAddress() + ":" + mobilePort + "/mobile";
    }

    private String lanAddress() {
        return LanAddressResolver.resolve();
    }

}

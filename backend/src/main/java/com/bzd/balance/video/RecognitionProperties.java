package com.bzd.balance.video;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recognition")
public class RecognitionProperties {

    private Map<String, List<String>> deviceProfiles = new LinkedHashMap<>();

    public Map<String, List<String>> getDeviceProfiles() {
        return deviceProfiles;
    }

    public void setDeviceProfiles(Map<String, List<String>> deviceProfiles) {
        this.deviceProfiles = deviceProfiles;
    }
}

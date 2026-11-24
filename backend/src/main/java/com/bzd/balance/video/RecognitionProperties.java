package com.bzd.balance.video;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.recognition")
public class RecognitionProperties {

    private Map<String, List<String>> deviceProfiles = new LinkedHashMap<>();
    private String genericProfile = "generic";
    private BigDecimal minimumConfidence = BigDecimal.valueOf(30);

    public Map<String, List<String>> getDeviceProfiles() {
        return deviceProfiles;
    }

    public void setDeviceProfiles(Map<String, List<String>> deviceProfiles) {
        this.deviceProfiles = deviceProfiles;
    }

    public String getGenericProfile() {
        return genericProfile;
    }

    public void setGenericProfile(String genericProfile) {
        this.genericProfile = genericProfile;
    }

    public BigDecimal getMinimumConfidence() {
        return minimumConfidence;
    }

    public void setMinimumConfidence(BigDecimal minimumConfidence) {
        this.minimumConfidence = minimumConfidence;
    }
}

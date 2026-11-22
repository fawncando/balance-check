package com.bzd.balance.video;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Video {

    private Long id;
    private String originalName;
    private String storedName;
    private String relativePath;
    private Long sizeBytes;
    private String sha256;
    private String contentType;
    private Long uploaderId;
    private Long balanceId;
    private LocalDateTime recordedAt;
    private LocalDateTime uploadedAt;
    private String status;
    private String message;
    private String personCard;
    private Boolean identityVerified;
    private String analysisProfile;
    private BigDecimal analysisConfidence;
    private Integer anomalyFrames;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public Long getBalanceId() {
        return balanceId;
    }

    public void setBalanceId(Long balanceId) {
        this.balanceId = balanceId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPersonCard() { return personCard; }
    public void setPersonCard(String personCard) { this.personCard = personCard; }
    public Boolean getIdentityVerified() { return identityVerified; }
    public void setIdentityVerified(Boolean identityVerified) { this.identityVerified = identityVerified; }
    public String getAnalysisProfile() { return analysisProfile; }
    public void setAnalysisProfile(String analysisProfile) { this.analysisProfile = analysisProfile; }
    public BigDecimal getAnalysisConfidence() { return analysisConfidence; }
    public void setAnalysisConfidence(BigDecimal analysisConfidence) { this.analysisConfidence = analysisConfidence; }
    public Integer getAnomalyFrames() { return anomalyFrames; }
    public void setAnomalyFrames(Integer anomalyFrames) { this.anomalyFrames = anomalyFrames; }
}

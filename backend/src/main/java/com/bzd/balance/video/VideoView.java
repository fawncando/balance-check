package com.bzd.balance.video;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VideoView {

    private Long id;
    private String originalName;
    private String storedName;
    private Boolean sourceAvailable;
    private Long sizeBytes;
    private Long balanceId;
    private String balanceCode;
    private Long calibrationId;
    private String calibrationStatus;
    private String calibrationExternalResult;
    private String calibrationReviewResult;
    private String calibrationReviewComment;
    private String uploaderName;
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

    /** 源视频文件是否仍在服务器上（识别成功按策略清理后为 false）。 */
    public Boolean getSourceAvailable() {
        return sourceAvailable;
    }

    public void setSourceAvailable(Boolean sourceAvailable) {
        this.sourceAvailable = sourceAvailable;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Long getBalanceId() {
        return balanceId;
    }

    public void setBalanceId(Long balanceId) {
        this.balanceId = balanceId;
    }

    public String getBalanceCode() {
        return balanceCode;
    }

    public void setBalanceCode(String balanceCode) {
        this.balanceCode = balanceCode;
    }

    public Long getCalibrationId() {
        return calibrationId;
    }

    public void setCalibrationId(Long calibrationId) {
        this.calibrationId = calibrationId;
    }

    public String getCalibrationStatus() {
        return calibrationStatus;
    }

    public void setCalibrationStatus(String calibrationStatus) {
        this.calibrationStatus = calibrationStatus;
    }

    public String getCalibrationExternalResult() {
        return calibrationExternalResult;
    }

    public void setCalibrationExternalResult(String calibrationExternalResult) {
        this.calibrationExternalResult = calibrationExternalResult;
    }

    public String getCalibrationReviewResult() {
        return calibrationReviewResult;
    }

    public void setCalibrationReviewResult(String calibrationReviewResult) {
        this.calibrationReviewResult = calibrationReviewResult;
    }

    public String getCalibrationReviewComment() {
        return calibrationReviewComment;
    }

    public void setCalibrationReviewComment(String calibrationReviewComment) {
        this.calibrationReviewComment = calibrationReviewComment;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public void setUploaderName(String uploaderName) {
        this.uploaderName = uploaderName;
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

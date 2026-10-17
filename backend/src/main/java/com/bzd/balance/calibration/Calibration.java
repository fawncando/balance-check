package com.bzd.balance.calibration;

import java.time.LocalDateTime;

public class Calibration {

    private Long id;
    private String recordNo;
    private Long balanceId;
    private Long videoId;
    private Long inspectorId;
    private LocalDateTime calibrationTime;
    private Boolean cleanOk;
    private Boolean levelOk;
    private String internalCalibrationResult;
    private String externalResult;
    private String remark;
    private String status;
    private LocalDateTime submittedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
    public Long getBalanceId() { return balanceId; }
    public void setBalanceId(Long balanceId) { this.balanceId = balanceId; }
    public Long getVideoId() { return videoId; }
    public void setVideoId(Long videoId) { this.videoId = videoId; }
    public Long getInspectorId() { return inspectorId; }
    public void setInspectorId(Long inspectorId) { this.inspectorId = inspectorId; }
    public LocalDateTime getCalibrationTime() { return calibrationTime; }
    public void setCalibrationTime(LocalDateTime calibrationTime) { this.calibrationTime = calibrationTime; }
    public Boolean getCleanOk() { return cleanOk; }
    public void setCleanOk(Boolean cleanOk) { this.cleanOk = cleanOk; }
    public Boolean getLevelOk() { return levelOk; }
    public void setLevelOk(Boolean levelOk) { this.levelOk = levelOk; }
    public String getInternalCalibrationResult() { return internalCalibrationResult; }
    public void setInternalCalibrationResult(String value) { this.internalCalibrationResult = value; }
    public String getExternalResult() { return externalResult; }
    public void setExternalResult(String externalResult) { this.externalResult = externalResult; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}

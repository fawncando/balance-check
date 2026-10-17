package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CalibrationDtos {

    private CalibrationDtos() {
    }

    public static class MeasurementInput {
        private Integer rangeSeq = 1;
        private Integer weightSeq;
        private Integer measureSeq;
        private BigDecimal nominalG;
        private BigDecimal displayValue;
        private String displayUnit = "g";
        private LocalDateTime measuredAt;
        private Integer videoSecond;
        private BigDecimal confidence;
        private Boolean manuallyCorrected;
        private String rawText;

        public Integer getRangeSeq() { return rangeSeq; }
        public void setRangeSeq(Integer rangeSeq) { this.rangeSeq = rangeSeq; }
        public Integer getWeightSeq() { return weightSeq; }
        public void setWeightSeq(Integer weightSeq) { this.weightSeq = weightSeq; }
        public Integer getMeasureSeq() { return measureSeq; }
        public void setMeasureSeq(Integer measureSeq) { this.measureSeq = measureSeq; }
        public BigDecimal getNominalG() { return nominalG; }
        public void setNominalG(BigDecimal nominalG) { this.nominalG = nominalG; }
        public BigDecimal getDisplayValue() { return displayValue; }
        public void setDisplayValue(BigDecimal displayValue) { this.displayValue = displayValue; }
        public String getDisplayUnit() { return displayUnit; }
        public void setDisplayUnit(String displayUnit) { this.displayUnit = displayUnit; }
        public LocalDateTime getMeasuredAt() { return measuredAt; }
        public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }
        public Integer getVideoSecond() { return videoSecond; }
        public void setVideoSecond(Integer videoSecond) { this.videoSecond = videoSecond; }
        public BigDecimal getConfidence() { return confidence; }
        public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
        public Boolean getManuallyCorrected() { return manuallyCorrected; }
        public void setManuallyCorrected(Boolean manuallyCorrected) { this.manuallyCorrected = manuallyCorrected; }
        public String getRawText() { return rawText; }
        public void setRawText(String rawText) { this.rawText = rawText; }
    }

    public static class SaveRequest {
        private Long balanceId;
        private Long videoId;
        private LocalDateTime calibrationTime;
        private Boolean cleanOk;
        private Boolean levelOk;
        private String internalCalibrationResult;
        private String remark;
        private List<MeasurementInput> measurements;

        public Long getBalanceId() { return balanceId; }
        public void setBalanceId(Long balanceId) { this.balanceId = balanceId; }
        public Long getVideoId() { return videoId; }
        public void setVideoId(Long videoId) { this.videoId = videoId; }
        public LocalDateTime getCalibrationTime() { return calibrationTime; }
        public void setCalibrationTime(LocalDateTime calibrationTime) { this.calibrationTime = calibrationTime; }
        public Boolean getCleanOk() { return cleanOk; }
        public void setCleanOk(Boolean cleanOk) { this.cleanOk = cleanOk; }
        public Boolean getLevelOk() { return levelOk; }
        public void setLevelOk(Boolean levelOk) { this.levelOk = levelOk; }
        public String getInternalCalibrationResult() { return internalCalibrationResult; }
        public void setInternalCalibrationResult(String value) { this.internalCalibrationResult = value; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public List<MeasurementInput> getMeasurements() { return measurements; }
        public void setMeasurements(List<MeasurementInput> measurements) { this.measurements = measurements; }
    }

    public static class RecordView {
        private Long id;
        private String recordNo;
        private Long balanceId;
        private String balanceCode;
        private LocalDateTime calibrationTime;
        private String status;
        private String externalResult;
        private Boolean cleanOk;
        private Boolean levelOk;
        private String internalCalibrationResult;
        private String remark;
        private String inspectorName;
        private LocalDateTime submittedAt;
        private List<CalibrationMeasurement> measurements;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRecordNo() { return recordNo; }
        public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
        public Long getBalanceId() { return balanceId; }
        public void setBalanceId(Long balanceId) { this.balanceId = balanceId; }
        public String getBalanceCode() { return balanceCode; }
        public void setBalanceCode(String balanceCode) { this.balanceCode = balanceCode; }
        public LocalDateTime getCalibrationTime() { return calibrationTime; }
        public void setCalibrationTime(LocalDateTime calibrationTime) { this.calibrationTime = calibrationTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getExternalResult() { return externalResult; }
        public void setExternalResult(String externalResult) { this.externalResult = externalResult; }
        public Boolean getCleanOk() { return cleanOk; }
        public void setCleanOk(Boolean cleanOk) { this.cleanOk = cleanOk; }
        public Boolean getLevelOk() { return levelOk; }
        public void setLevelOk(Boolean levelOk) { this.levelOk = levelOk; }
        public String getInternalCalibrationResult() { return internalCalibrationResult; }
        public void setInternalCalibrationResult(String value) { this.internalCalibrationResult = value; }
        public String getRemark() { return remark; }
        public void setRemark(String remark) { this.remark = remark; }
        public String getInspectorName() { return inspectorName; }
        public void setInspectorName(String inspectorName) { this.inspectorName = inspectorName; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public List<CalibrationMeasurement> getMeasurements() { return measurements; }
        public void setMeasurements(List<CalibrationMeasurement> measurements) { this.measurements = measurements; }
    }
}

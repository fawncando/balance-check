package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

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

    /** 记录查询条件：按天平、状态、记录编号与日期段过滤，空值表示不限。 */
    public static class Query {
        private Long balanceId;
        private String status;
        private String recordNo;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateFrom;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateTo;

        public Long getBalanceId() { return balanceId; }
        public void setBalanceId(Long balanceId) { this.balanceId = balanceId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRecordNo() { return recordNo; }
        public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
        public LocalDate getDateFrom() { return dateFrom; }
        public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
        public LocalDate getDateTo() { return dateTo; }
        public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    }

    /** 复核请求：result 取 APPROVED 或 REJECTED，退回时 comment 必填。 */
    public static class ReviewRequest {
        private String result;
        private String comment;

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }

    public static class RecordView {
        private Long id;
        private String recordNo;
        private Long balanceId;
        private String balanceCode;
        private String balanceUniqueNo;
        private String department;
        private LocalDateTime calibrationTime;
        private String status;
        private String externalResult;
        private Boolean cleanOk;
        private Boolean levelOk;
        private String internalCalibrationResult;
        private String remark;
        private Long inspectorId;
        private String inspectorName;
        private LocalDateTime submittedAt;
        private String reviewerName;
        private LocalDateTime reviewedAt;
        private String reviewResult;
        private String reviewComment;
        private List<CalibrationMeasurement> measurements;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getRecordNo() { return recordNo; }
        public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
        public Long getBalanceId() { return balanceId; }
        public void setBalanceId(Long balanceId) { this.balanceId = balanceId; }
        public String getBalanceCode() { return balanceCode; }
        public void setBalanceCode(String balanceCode) { this.balanceCode = balanceCode; }
        public String getBalanceUniqueNo() { return balanceUniqueNo; }
        public void setBalanceUniqueNo(String balanceUniqueNo) { this.balanceUniqueNo = balanceUniqueNo; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
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
        public Long getInspectorId() { return inspectorId; }
        public void setInspectorId(Long inspectorId) { this.inspectorId = inspectorId; }
        public String getInspectorName() { return inspectorName; }
        public void setInspectorName(String inspectorName) { this.inspectorName = inspectorName; }
        public LocalDateTime getSubmittedAt() { return submittedAt; }
        public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
        public String getReviewerName() { return reviewerName; }
        public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
        public LocalDateTime getReviewedAt() { return reviewedAt; }
        public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
        public String getReviewResult() { return reviewResult; }
        public void setReviewResult(String reviewResult) { this.reviewResult = reviewResult; }
        public String getReviewComment() { return reviewComment; }
        public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
        public List<CalibrationMeasurement> getMeasurements() { return measurements; }
        public void setMeasurements(List<CalibrationMeasurement> measurements) { this.measurements = measurements; }
    }
}

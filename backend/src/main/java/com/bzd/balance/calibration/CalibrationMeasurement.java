package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CalibrationMeasurement {

    private Long id;
    private Long calibrationId;
    private Integer rangeSeq;
    private Integer weightSeq;
    private Integer measureSeq;
    private BigDecimal nominalG;
    private BigDecimal lowerG;
    private BigDecimal upperG;
    private BigDecimal displayValue;
    private String displayUnit;
    private BigDecimal valueG;
    private LocalDateTime measuredAt;
    private Integer videoSecond;
    private Boolean qualified;
    private BigDecimal confidence;
    private Boolean manuallyCorrected;
    private String rawText;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCalibrationId() { return calibrationId; }
    public void setCalibrationId(Long calibrationId) { this.calibrationId = calibrationId; }
    public Integer getRangeSeq() { return rangeSeq; }
    public void setRangeSeq(Integer rangeSeq) { this.rangeSeq = rangeSeq; }
    public Integer getWeightSeq() { return weightSeq; }
    public void setWeightSeq(Integer weightSeq) { this.weightSeq = weightSeq; }
    public Integer getMeasureSeq() { return measureSeq; }
    public void setMeasureSeq(Integer measureSeq) { this.measureSeq = measureSeq; }
    public BigDecimal getNominalG() { return nominalG; }
    public void setNominalG(BigDecimal nominalG) { this.nominalG = nominalG; }
    public BigDecimal getLowerG() { return lowerG; }
    public void setLowerG(BigDecimal lowerG) { this.lowerG = lowerG; }
    public BigDecimal getUpperG() { return upperG; }
    public void setUpperG(BigDecimal upperG) { this.upperG = upperG; }
    public BigDecimal getDisplayValue() { return displayValue; }
    public void setDisplayValue(BigDecimal displayValue) { this.displayValue = displayValue; }
    public String getDisplayUnit() { return displayUnit; }
    public void setDisplayUnit(String displayUnit) { this.displayUnit = displayUnit; }
    public BigDecimal getValueG() { return valueG; }
    public void setValueG(BigDecimal valueG) { this.valueG = valueG; }
    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public void setMeasuredAt(LocalDateTime measuredAt) { this.measuredAt = measuredAt; }
    public Integer getVideoSecond() { return videoSecond; }
    public void setVideoSecond(Integer videoSecond) { this.videoSecond = videoSecond; }
    public Boolean getQualified() { return qualified; }
    public void setQualified(Boolean qualified) { this.qualified = qualified; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public Boolean getManuallyCorrected() { return manuallyCorrected; }
    public void setManuallyCorrected(Boolean manuallyCorrected) { this.manuallyCorrected = manuallyCorrected; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
}

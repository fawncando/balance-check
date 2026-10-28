package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.List;

/** 天平清单维护请求：基本信息与量程一起提交，量程整体替换。 */
public class BalanceRequest {

    private String code;
    private String uniqueNo;
    private String department;
    private Integer rangeCount;
    private String internalCalibrationMode;
    private String defaultUnit;
    private String status;
    private List<RangeInput> ranges;

    public static class RangeInput {
        private Integer seqNo;
        private BigDecimal maxCapacityG;
        private BigDecimal divisionMg;
        private Long weightSetId;

        public Integer getSeqNo() { return seqNo; }
        public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
        public BigDecimal getMaxCapacityG() { return maxCapacityG; }
        public void setMaxCapacityG(BigDecimal maxCapacityG) { this.maxCapacityG = maxCapacityG; }
        public BigDecimal getDivisionMg() { return divisionMg; }
        public void setDivisionMg(BigDecimal divisionMg) { this.divisionMg = divisionMg; }
        public Long getWeightSetId() { return weightSetId; }
        public void setWeightSetId(Long weightSetId) { this.weightSetId = weightSetId; }
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getUniqueNo() { return uniqueNo; }
    public void setUniqueNo(String uniqueNo) { this.uniqueNo = uniqueNo; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Integer getRangeCount() { return rangeCount; }
    public void setRangeCount(Integer rangeCount) { this.rangeCount = rangeCount; }
    public String getInternalCalibrationMode() { return internalCalibrationMode; }
    public void setInternalCalibrationMode(String value) { this.internalCalibrationMode = value; }
    public String getDefaultUnit() { return defaultUnit; }
    public void setDefaultUnit(String defaultUnit) { this.defaultUnit = defaultUnit; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<RangeInput> getRanges() { return ranges; }
    public void setRanges(List<RangeInput> ranges) { this.ranges = ranges; }
}

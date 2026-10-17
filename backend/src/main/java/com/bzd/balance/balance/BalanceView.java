package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BalanceView {

    private Long id;
    private String code;
    private String uniqueNo;
    private String department;
    private Integer rangeCount;
    private String internalCalibrationMode;
    private String defaultUnit;
    private String status;
    private List<RangeView> ranges = new ArrayList<>();

    public static class RangeView {
        private Integer seqNo;
        private BigDecimal maxCapacityG;
        private BigDecimal divisionMg;
        private String weightSetNo;
        private List<BigDecimal> weights = new ArrayList<>();

        public Integer getSeqNo() {
            return seqNo;
        }

        public void setSeqNo(Integer seqNo) {
            this.seqNo = seqNo;
        }

        public BigDecimal getMaxCapacityG() {
            return maxCapacityG;
        }

        public void setMaxCapacityG(BigDecimal maxCapacityG) {
            this.maxCapacityG = maxCapacityG;
        }

        public BigDecimal getDivisionMg() {
            return divisionMg;
        }

        public void setDivisionMg(BigDecimal divisionMg) {
            this.divisionMg = divisionMg;
        }

        public String getWeightSetNo() {
            return weightSetNo;
        }

        public void setWeightSetNo(String weightSetNo) {
            this.weightSetNo = weightSetNo;
        }

        public List<BigDecimal> getWeights() {
            return weights;
        }

        public void setWeights(List<BigDecimal> weights) {
            this.weights = weights;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getUniqueNo() {
        return uniqueNo;
    }

    public void setUniqueNo(String uniqueNo) {
        this.uniqueNo = uniqueNo;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getRangeCount() {
        return rangeCount;
    }

    public void setRangeCount(Integer rangeCount) {
        this.rangeCount = rangeCount;
    }

    public String getInternalCalibrationMode() {
        return internalCalibrationMode;
    }

    public void setInternalCalibrationMode(String internalCalibrationMode) {
        this.internalCalibrationMode = internalCalibrationMode;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    public void setDefaultUnit(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<RangeView> getRanges() {
        return ranges;
    }

    public void setRanges(List<RangeView> ranges) {
        this.ranges = ranges;
    }
}

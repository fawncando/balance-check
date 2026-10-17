package com.bzd.balance.balance;

import java.math.BigDecimal;

public class BalanceRange {

    private Long id;
    private Long balanceId;
    private Integer seqNo;
    private BigDecimal maxCapacityG;
    private BigDecimal divisionMg;
    private Long weightSetId;
    private String weightSetNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBalanceId() {
        return balanceId;
    }

    public void setBalanceId(Long balanceId) {
        this.balanceId = balanceId;
    }

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

    public Long getWeightSetId() {
        return weightSetId;
    }

    public void setWeightSetId(Long weightSetId) {
        this.weightSetId = weightSetId;
    }

    public String getWeightSetNo() {
        return weightSetNo;
    }

    public void setWeightSetNo(String weightSetNo) {
        this.weightSetNo = weightSetNo;
    }
}

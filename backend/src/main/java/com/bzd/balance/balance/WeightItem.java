package com.bzd.balance.balance;

import java.math.BigDecimal;

public class WeightItem {

    private Long weightSetId;
    private Integer seqNo;
    private BigDecimal nominalG;

    public Long getWeightSetId() {
        return weightSetId;
    }

    public void setWeightSetId(Long weightSetId) {
        this.weightSetId = weightSetId;
    }

    public Integer getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Integer seqNo) {
        this.seqNo = seqNo;
    }

    public BigDecimal getNominalG() {
        return nominalG;
    }

    public void setNominalG(BigDecimal nominalG) {
        this.nominalG = nominalG;
    }
}

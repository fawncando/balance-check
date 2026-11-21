package com.bzd.balance.video;

import java.math.BigDecimal;
import java.util.List;

public class RecognitionResult {

    private String profile;
    private String verdict;
    private List<Weighing> weighings;

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public List<Weighing> getWeighings() {
        return weighings;
    }

    public void setWeighings(List<Weighing> weighings) {
        this.weighings = weighings;
    }

    public static class Weighing {
        private Integer weightSeq;
        private Integer measureSeq;
        private BigDecimal nominalG;
        private BigDecimal valueG;
        private Integer startSec;
        private Integer endSec;

        public Integer getWeightSeq() {
            return weightSeq;
        }

        public void setWeightSeq(Integer weightSeq) {
            this.weightSeq = weightSeq;
        }

        public Integer getMeasureSeq() {
            return measureSeq;
        }

        public void setMeasureSeq(Integer measureSeq) {
            this.measureSeq = measureSeq;
        }

        public BigDecimal getNominalG() {
            return nominalG;
        }

        public void setNominalG(BigDecimal nominalG) {
            this.nominalG = nominalG;
        }

        public BigDecimal getValueG() {
            return valueG;
        }

        public void setValueG(BigDecimal valueG) {
            this.valueG = valueG;
        }

        public Integer getStartSec() {
            return startSec;
        }

        public void setStartSec(Integer startSec) {
            this.startSec = startSec;
        }

        public Integer getEndSec() {
            return endSec;
        }

        public void setEndSec(Integer endSec) {
            this.endSec = endSec;
        }
    }
}

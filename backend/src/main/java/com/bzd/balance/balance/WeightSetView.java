package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 砝码组及其盒内砝码，供天平量程选择。 */
public class WeightSetView {

    private Long id;
    private String setNo;
    private String grade;
    private LocalDate validUntil;
    private String note;
    private List<BigDecimal> weights = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSetNo() { return setNo; }
    public void setSetNo(String setNo) { this.setNo = setNo; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<BigDecimal> getWeights() { return weights; }
    public void setWeights(List<BigDecimal> weights) { this.weights = weights; }
}

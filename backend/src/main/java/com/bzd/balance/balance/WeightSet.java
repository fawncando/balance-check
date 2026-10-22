package com.bzd.balance.balance;

import java.time.LocalDate;

public class WeightSet {

    private Long id;
    private String setNo;
    private String grade;
    private LocalDate validUntil;
    private String note;

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
}

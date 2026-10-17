package com.bzd.balance.balance;

import java.time.LocalDateTime;

public class Balance {

    private Long id;
    private String code;
    private String uniqueNo;
    private String department;
    private Integer rangeCount;
    private String internalCalibrationMode;
    private String defaultUnit;
    private String status;
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

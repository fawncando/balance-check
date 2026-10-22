package com.bzd.balance.log;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** 操作日志查询条件：操作类型、操作人与日期段，其余为分页。 */
public class AuditLogQuery {

    private String action;
    private Long userId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;
    private Integer page = 1;
    private Integer size = 20;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}

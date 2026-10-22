package com.bzd.balance.log;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.common.PageView;

/** 操作日志查询：只对管理员开放，分页返回。 */
@Service
public class AuditLogService {

    private static final int MAX_SIZE = 200;

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public PageView<AuditLogView> search(AuditLogQuery query, SessionUser user) {
        if (!user.isAdmin()) {
            throw new BizException(403, "只有管理员可以查看操作日志");
        }
        AuditLogQuery condition = query == null ? new AuditLogQuery() : query;
        int page = condition.getPage() == null || condition.getPage() < 1 ? 1 : condition.getPage();
        int size = condition.getSize() == null || condition.getSize() < 1 ? 20 : Math.min(condition.getSize(), MAX_SIZE);
        LocalDate dateFrom = condition.getDateFrom();
        LocalDate dateTo = condition.getDateTo();
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        LocalDateTime timeFrom = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime timeTo = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();
        String action = trimToNull(condition.getAction());
        long total = auditLogMapper.count(action, condition.getUserId(), timeFrom, timeTo);
        List<AuditLogView> items = total == 0
                ? List.of()
                : auditLogMapper.search(action, condition.getUserId(), timeFrom, timeTo,
                        size, (page - 1) * size);
        return new PageView<>(items, total, page, size);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

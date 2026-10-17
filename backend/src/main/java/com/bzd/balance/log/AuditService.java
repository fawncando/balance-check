package com.bzd.balance.log;

import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void log(Long userId, String action, String targetType, String targetId, String detail, String ip) {
        auditLogMapper.insert(userId, action, targetType, targetId, detail, ip);
    }
}

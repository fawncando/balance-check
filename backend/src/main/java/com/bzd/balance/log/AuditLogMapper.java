package com.bzd.balance.log;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO audit_log (user_id, action, target_type, target_id, detail, ip)
            VALUES (#{userId}, #{action}, #{targetType}, #{targetId}, #{detail}, #{ip})
            """)
    void insert(@Param("userId") Long userId,
                @Param("action") String action,
                @Param("targetType") String targetType,
                @Param("targetId") String targetId,
                @Param("detail") String detail,
                @Param("ip") String ip);
}

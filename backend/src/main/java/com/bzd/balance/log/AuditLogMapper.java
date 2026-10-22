package com.bzd.balance.log;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {

    String CONDITION = """
            WHERE (#{action,jdbcType=VARCHAR} IS NULL OR a.action = #{action,jdbcType=VARCHAR})
              AND (#{userId,jdbcType=BIGINT} IS NULL OR a.user_id = #{userId,jdbcType=BIGINT})
              AND (#{timeFrom,jdbcType=TIMESTAMP} IS NULL
                   OR a.created_at >= #{timeFrom,jdbcType=TIMESTAMP})
              AND (#{timeTo,jdbcType=TIMESTAMP} IS NULL
                   OR a.created_at < #{timeTo,jdbcType=TIMESTAMP})
            """;

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

    @Select("""
            SELECT a.id, a.user_id, u.display_name AS user_name, a.action, a.target_type,
                   a.target_id, a.detail, a.ip, a.created_at
            FROM audit_log a
            LEFT JOIN sys_user u ON u.id = a.user_id
            """ + CONDITION + """
            ORDER BY a.id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AuditLogView> search(@Param("action") String action,
                              @Param("userId") Long userId,
                              @Param("timeFrom") LocalDateTime timeFrom,
                              @Param("timeTo") LocalDateTime timeTo,
                              @Param("size") int size,
                              @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*) FROM audit_log a
            """ + CONDITION)
    long count(@Param("action") String action,
               @Param("userId") Long userId,
               @Param("timeFrom") LocalDateTime timeFrom,
               @Param("timeTo") LocalDateTime timeTo);
}

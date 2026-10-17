package com.bzd.balance.calibration;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewMapper {

    /**
     * 写入复核结论。一条记录只保留一条复核结论（review 表唯一约束），
     * 退回后重新复核时覆盖上一条，历次动作由 audit_log 留痕。
     */
    @Insert("""
            INSERT INTO review (calibration_id, reviewer_id, reviewed_at, result, comment)
            VALUES (#{calibrationId}, #{reviewerId}, NOW(), #{result}, #{comment})
            ON DUPLICATE KEY UPDATE
                reviewer_id = #{reviewerId},
                reviewed_at = NOW(),
                result = #{result},
                comment = #{comment}
            """)
    void upsert(@Param("calibrationId") Long calibrationId,
                @Param("reviewerId") Long reviewerId,
                @Param("result") String result,
                @Param("comment") String comment);
}

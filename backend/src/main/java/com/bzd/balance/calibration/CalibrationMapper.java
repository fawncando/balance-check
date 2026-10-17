package com.bzd.balance.calibration;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CalibrationMapper {

    @Insert("""
            INSERT INTO calibration (record_no, balance_id, video_id, inspector_id, calibration_time,
                                     clean_ok, level_ok, internal_calibration_result, external_result,
                                     remark, status)
            VALUES (#{recordNo}, #{balanceId}, #{videoId}, #{inspectorId}, #{calibrationTime},
                    #{cleanOk}, #{levelOk}, #{internalCalibrationResult}, #{externalResult},
                    #{remark}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Calibration record);

    @Insert("""
            INSERT INTO calibration_measurement (calibration_id, range_seq, weight_seq, measure_seq,
                    nominal_g, lower_g, upper_g, display_value, display_unit, value_g,
                    measured_at, video_second, qualified, confidence, manually_corrected, raw_text)
            VALUES (#{calibrationId}, #{rangeSeq}, #{weightSeq}, #{measureSeq},
                    #{nominalG}, #{lowerG}, #{upperG}, #{displayValue}, #{displayUnit}, #{valueG},
                    #{measuredAt}, #{videoSecond}, #{qualified}, #{confidence}, #{manuallyCorrected}, #{rawText})
            """)
    void insertMeasurement(CalibrationMeasurement measurement);

    @Delete("DELETE FROM calibration_measurement WHERE calibration_id = #{id}")
    void deleteMeasurements(Long id);

    @Select("SELECT * FROM calibration WHERE id = #{id}")
    Calibration findById(Long id);

    /** 记录详情：带出天平、检验人员与复核结论。 */
    @Select("""
            SELECT c.id, c.record_no, c.balance_id, b.code AS balance_code, b.unique_no AS balance_unique_no,
                   b.department, c.calibration_time, c.status, c.external_result, c.clean_ok, c.level_ok,
                   c.internal_calibration_result, c.remark, c.inspector_id,
                   u.display_name AS inspector_name, c.submitted_at,
                   rv.reviewed_at AS reviewed_at, rv.result AS review_result, rv.comment AS review_comment,
                   ru.display_name AS reviewer_name
            FROM calibration c
            JOIN balance b ON b.id = c.balance_id
            JOIN sys_user u ON u.id = c.inspector_id
            LEFT JOIN review rv ON rv.calibration_id = c.id
            LEFT JOIN sys_user ru ON ru.id = rv.reviewer_id
            WHERE c.id = #{id}
            """)
    CalibrationDtos.RecordView findView(@Param("id") Long id);

    @Select("""
            SELECT * FROM calibration_measurement
            WHERE calibration_id = #{id}
            ORDER BY range_seq, weight_seq, measure_seq
            """)
    List<CalibrationMeasurement> findMeasurements(Long id);

    /**
     * 记录查询：各条件为空表示不限。
     * inspectorId 由服务层按角色决定，检验人员只能查到自己创建的记录。
     */
    @Select("""
            SELECT c.id, c.record_no, c.balance_id, b.code AS balance_code, b.unique_no AS balance_unique_no,
                   b.department, c.calibration_time, c.status, c.external_result, c.clean_ok, c.level_ok,
                   c.internal_calibration_result, c.remark, c.inspector_id,
                   u.display_name AS inspector_name, c.submitted_at,
                   rv.reviewed_at AS reviewed_at, rv.result AS review_result, rv.comment AS review_comment,
                   ru.display_name AS reviewer_name
            FROM calibration c
            JOIN balance b ON b.id = c.balance_id
            JOIN sys_user u ON u.id = c.inspector_id
            LEFT JOIN review rv ON rv.calibration_id = c.id
            LEFT JOIN sys_user ru ON ru.id = rv.reviewer_id
            WHERE (#{inspectorId,jdbcType=BIGINT} IS NULL OR c.inspector_id = #{inspectorId,jdbcType=BIGINT})
              AND (#{balanceId,jdbcType=BIGINT} IS NULL OR c.balance_id = #{balanceId,jdbcType=BIGINT})
              AND (#{status,jdbcType=VARCHAR} IS NULL OR c.status = #{status,jdbcType=VARCHAR})
              AND (#{recordNo,jdbcType=VARCHAR} IS NULL
                   OR c.record_no LIKE CONCAT('%', #{recordNo,jdbcType=VARCHAR}, '%'))
              AND (#{timeFrom,jdbcType=TIMESTAMP} IS NULL
                   OR c.calibration_time >= #{timeFrom,jdbcType=TIMESTAMP})
              AND (#{timeTo,jdbcType=TIMESTAMP} IS NULL
                   OR c.calibration_time < #{timeTo,jdbcType=TIMESTAMP})
            ORDER BY c.calibration_time DESC, c.id DESC
            """)
    List<CalibrationDtos.RecordView> search(@Param("inspectorId") Long inspectorId,
                                            @Param("balanceId") Long balanceId,
                                            @Param("status") String status,
                                            @Param("recordNo") String recordNo,
                                            @Param("timeFrom") LocalDateTime timeFrom,
                                            @Param("timeTo") LocalDateTime timeTo);

    @Update("""
            UPDATE calibration
            SET calibration_time = #{calibrationTime}, clean_ok = #{cleanOk}, level_ok = #{levelOk},
                internal_calibration_result = #{internalCalibrationResult},
                external_result = #{externalResult}, remark = #{remark}
            WHERE id = #{id}
            """)
    void updateDraft(Calibration record);

    @Update("""
            UPDATE calibration
            SET status = 'SUBMITTED', submitted_at = NOW()
            WHERE id = #{id} AND status = 'DRAFT'
            """)
    int submit(Long id);

    /** 复核落库：通过则记为 REVIEWED 并记锁定时间，退回则回到 DRAFT 并清空锁定时间。 */
    @Update("""
            UPDATE calibration
            SET status = #{status},
                reviewed_at = CASE WHEN #{status} = 'REVIEWED' THEN NOW() ELSE NULL END
            WHERE id = #{id} AND status = 'SUBMITTED'
            """)
    int markReviewed(@Param("id") Long id, @Param("status") String status);

    /** 复核人员解锁：把已复核记录退回草稿，交回检验人员修改。 */
    @Update("""
            UPDATE calibration
            SET status = 'DRAFT', reviewed_at = NULL
            WHERE id = #{id} AND status = 'REVIEWED'
            """)
    int reopen(Long id);
}

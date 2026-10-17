package com.bzd.balance.calibration;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
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

    @Select("""
            SELECT c.id, c.record_no, c.balance_id, b.code AS balance_code, c.calibration_time,
                   c.status, c.external_result, c.clean_ok, c.level_ok, c.internal_calibration_result,
                   c.remark, u.display_name AS inspector_name, c.submitted_at
            FROM calibration c
            JOIN balance b ON b.id = c.balance_id
            JOIN sys_user u ON u.id = c.inspector_id
            WHERE c.id = #{id}
            """)
    CalibrationDtos.RecordView findView(Long id);

    @Select("""
            SELECT * FROM calibration_measurement
            WHERE calibration_id = #{id}
            ORDER BY range_seq, weight_seq, measure_seq
            """)
    List<CalibrationMeasurement> findMeasurements(Long id);

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

    @Select("""
            SELECT c.id, c.record_no, c.balance_id, b.code AS balance_code, c.calibration_time,
                   c.status, c.external_result, u.display_name AS inspector_name, c.submitted_at
            FROM calibration c
            JOIN balance b ON b.id = c.balance_id
            JOIN sys_user u ON u.id = c.inspector_id
            ORDER BY c.id DESC
            """)
    List<CalibrationDtos.RecordView> findAll();
}

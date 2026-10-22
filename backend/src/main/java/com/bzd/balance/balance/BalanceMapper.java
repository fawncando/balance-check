package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BalanceMapper {

    @Select("""
            SELECT id, code, unique_no, department, range_count, internal_calibration_mode,
                   default_unit, status, created_at
            FROM balance
            WHERE status = 'ACTIVE'
            ORDER BY code
            """)
    List<Balance> findAllActive();

    @Select("""
            SELECT id, code, unique_no, department, range_count, internal_calibration_mode,
                   default_unit, status, created_at
            FROM balance
            WHERE id = #{id}
            """)
    Balance findById(Long id);

    @Select("""
            SELECT r.id, r.balance_id, r.seq_no, r.max_capacity_g, r.division_mg,
                   r.weight_set_id, w.set_no AS weight_set_no
            FROM balance_range r
            JOIN weight_set w ON w.id = r.weight_set_id
            ORDER BY r.balance_id, r.seq_no
            """)
    List<BalanceRange> findAllRanges();

    @Select("""
            SELECT r.id, r.balance_id, r.seq_no, r.max_capacity_g, r.division_mg,
                   r.weight_set_id, w.set_no AS weight_set_no
            FROM balance_range r
            JOIN weight_set w ON w.id = r.weight_set_id
            WHERE r.balance_id = #{balanceId}
            ORDER BY r.seq_no
            """)
    List<BalanceRange> findRangesByBalance(Long balanceId);

    @Select("""
            SELECT weight_set_id, seq_no, nominal_g
            FROM weight_set_item
            ORDER BY weight_set_id, seq_no
            """)
    List<WeightItem> findAllWeightItems();

    @Select("""
            SELECT id, set_no, grade, valid_until, note
            FROM weight_set
            ORDER BY set_no
            """)
    List<WeightSet> findAllWeightSets();

    @Select("SELECT COUNT(*) FROM weight_set WHERE id = #{id}")
    int countWeightSet(Long id);

    @Insert("""
            INSERT INTO balance (code, unique_no, department, range_count, internal_calibration_mode,
                                 default_unit, status)
            VALUES (#{code}, #{uniqueNo}, #{department}, #{rangeCount}, #{internalCalibrationMode},
                    #{defaultUnit}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Balance balance);

    @Update("""
            UPDATE balance
            SET code = #{code}, unique_no = #{uniqueNo}, department = #{department},
                range_count = #{rangeCount}, internal_calibration_mode = #{internalCalibrationMode},
                default_unit = #{defaultUnit}, status = #{status}
            WHERE id = #{id}
            """)
    void update(Balance balance);

    @Delete("DELETE FROM balance_range WHERE balance_id = #{balanceId}")
    void deleteRanges(Long balanceId);

    @Insert("""
            INSERT INTO balance_range (balance_id, seq_no, max_capacity_g, division_mg, weight_set_id)
            VALUES (#{balanceId}, #{seqNo}, #{maxCapacityG}, #{divisionMg}, #{weightSetId})
            """)
    void insertRange(@Param("balanceId") Long balanceId,
                     @Param("seqNo") int seqNo,
                     @Param("maxCapacityG") BigDecimal maxCapacityG,
                     @Param("divisionMg") BigDecimal divisionMg,
                     @Param("weightSetId") Long weightSetId);
}

package com.bzd.balance.balance;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
            SELECT weight_set_id, seq_no, nominal_g
            FROM weight_set_item
            ORDER BY weight_set_id, seq_no
            """)
    List<WeightItem> findAllWeightItems();
}

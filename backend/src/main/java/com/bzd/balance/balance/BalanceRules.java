package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 天平清单维护的校验规则：纯函数，返回 null 表示通过，否则返回给用户看的提示。
 * 拆出来是为了在没有数据库与 Maven 测试插件的环境里也能单独校验。
 */
public final class BalanceRules {

    public static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    public static final Set<String> INTERNAL_MODES = Set.of("自动", "手动", "NA");
    public static final Set<String> UNITS = Set.of("g", "mg");

    private BalanceRules() {
    }

    public static String checkCode(String code) {
        if (code == null || code.isBlank()) {
            return "请填写天平编号";
        }
        String value = code.trim();
        if (!value.matches("[A-Za-z][A-Za-z0-9]{1,19}")) {
            return "天平编号需以字母开头，2 至 20 位字母或数字";
        }
        return null;
    }

    public static String checkUniqueNo(String uniqueNo) {
        if (uniqueNo == null || uniqueNo.isBlank()) {
            return "请填写天平唯一号";
        }
        if (uniqueNo.trim().length() > 40) {
            return "天平唯一号不能超过 40 个字符";
        }
        return null;
    }

    public static String checkDepartment(String department) {
        if (department == null || department.isBlank()) {
            return "请填写科室";
        }
        return department.trim().length() > 50 ? "科室不能超过 50 个字符" : null;
    }

    public static String checkStatus(String status) {
        if (status == null || status.isBlank()) {
            return "请选择天平状态";
        }
        return STATUSES.contains(status) ? null : "天平状态只能是 ACTIVE 或 INACTIVE";
    }

    public static String checkInternalMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "请选择内部校正方式";
        }
        return INTERNAL_MODES.contains(mode) ? null : "内部校正方式只能是 自动、手动 或 NA";
    }

    public static String checkUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return "请选择默认单位";
        }
        return UNITS.contains(unit) ? null : "默认单位只能是 g 或 mg";
    }

    public static String checkRangeCount(Integer rangeCount) {
        if (rangeCount == null) {
            return "请填写量程数量";
        }
        return rangeCount == 1 || rangeCount == 2 ? null : "量程数量只能是 1 或 2";
    }

    /** 校验单个量程：序号从 1 连续、上限为正、分度值小于上限、必须选择砝码组。 */
    public static String checkRange(int seqNo, BigDecimal maxCapacityG, BigDecimal divisionMg, Long weightSetId) {
        if (seqNo < 1) {
            return "量程序号必须从 1 开始";
        }
        if (maxCapacityG == null || maxCapacityG.compareTo(BigDecimal.ZERO) <= 0) {
            return "量程上限必须大于 0";
        }
        if (divisionMg == null || divisionMg.compareTo(BigDecimal.ZERO) <= 0) {
            return "分度值必须大于 0";
        }
        if (divisionMg.divide(new BigDecimal("1000")).compareTo(maxCapacityG) >= 0) {
            return "分度值必须小于量程上限";
        }
        if (weightSetId == null) {
            return "请选择该量程使用的砝码组";
        }
        return null;
    }

    /** 量程列表与量程数量一致，且序号为 1..n 不重复。 */
    public static String checkRanges(Integer rangeCount, List<Integer> seqNumbers) {
        String countError = checkRangeCount(rangeCount);
        if (countError != null) {
            return countError;
        }
        if (seqNumbers == null || seqNumbers.isEmpty()) {
            return "请至少配置一个量程";
        }
        if (seqNumbers.size() != rangeCount) {
            return "量程数量与实际配置的 " + seqNumbers.size() + " 条量程不一致";
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer seq : seqNumbers) {
            if (seq == null || seq < 1 || seq > rangeCount) {
                return "量程序号必须为 1 到 " + rangeCount;
            }
            if (!seen.add(seq)) {
                return "量程序号不能重复";
            }
        }
        return null;
    }
}

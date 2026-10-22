import java.math.BigDecimal;
import java.util.List;

import com.bzd.balance.balance.BalanceRules;

/**
 * 天平清单校验规则的独立校验程序。
 *
 * 与批次 6 的 ReviewRulesCheck 一样，用 runner 绕开离线仓库缺失的 JUnit 依赖；
 * 由 scripts\batch7-unit-check.ps1 编译并执行。
 */
public class BalanceRulesCheck {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        check("合法编号通过，非法编号被拒绝",
                BalanceRules.checkCode("F003") == null
                        && BalanceRules.checkCode(" T901 ") == null
                        && BalanceRules.checkCode("001") != null
                        && BalanceRules.checkCode("A") != null
                        && BalanceRules.checkCode("F-001") != null
                        && BalanceRules.checkCode("") != null
                        && BalanceRules.checkCode(null) != null);

        check("设备唯一号与科室必填",
                BalanceRules.checkUniqueNo("DZTP170911C") == null
                        && BalanceRules.checkUniqueNo(" ") != null
                        && BalanceRules.checkDepartment("化学室") == null
                        && BalanceRules.checkDepartment(null) != null);

        check("状态只接受 ACTIVE 与 INACTIVE",
                BalanceRules.checkStatus("ACTIVE") == null
                        && BalanceRules.checkStatus("INACTIVE") == null
                        && BalanceRules.checkStatus("DELETED") != null);

        check("内部校正方式只接受 自动、手动、NA",
                BalanceRules.checkInternalMode("自动") == null
                        && BalanceRules.checkInternalMode("手动") == null
                        && BalanceRules.checkInternalMode("NA") == null
                        && BalanceRules.checkInternalMode("半自动") != null);

        check("默认单位只接受 g 与 mg",
                BalanceRules.checkUnit("g") == null
                        && BalanceRules.checkUnit("mg") == null
                        && BalanceRules.checkUnit("kg") != null);

        check("量程数量只能是 1 或 2",
                BalanceRules.checkRangeCount(1) == null
                        && BalanceRules.checkRangeCount(2) == null
                        && BalanceRules.checkRangeCount(3) != null
                        && BalanceRules.checkRangeCount(null) != null);

        check("量程上限与分度值必须为正且分度值小于上限",
                BalanceRules.checkRange(1, new BigDecimal("220"), new BigDecimal("0.1"), 1L) == null
                        && BalanceRules.checkRange(1, BigDecimal.ZERO, new BigDecimal("0.1"), 1L) != null
                        && BalanceRules.checkRange(1, new BigDecimal("220"), BigDecimal.ZERO, 1L) != null
                        && BalanceRules.checkRange(1, new BigDecimal("10"), new BigDecimal("20000"), 1L) != null
                        && BalanceRules.checkRange(1, new BigDecimal("220"), new BigDecimal("0.1"), null) != null
                        && BalanceRules.checkRange(0, new BigDecimal("220"), new BigDecimal("0.1"), 1L) != null);

        check("量程列表数量与序号必须一致",
                BalanceRules.checkRanges(2, List.of(1, 2)) == null
                        && BalanceRules.checkRanges(2, List.of(1)) != null
                        && BalanceRules.checkRanges(2, List.of(1, 1)) != null
                        && BalanceRules.checkRanges(2, List.of(1, 3)) != null
                        && BalanceRules.checkRanges(1, List.of()) != null);

        System.out.println();
        System.out.println("结果：" + pass + " 通过，" + fail + " 失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name);
        }
    }
}

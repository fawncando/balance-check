package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记录表渲染的独立校验：构造一条内存记录，检查表头、九行明细、复核信息与 HTML 转义。
 * 由 scripts\batch6-unit-check.ps1 编译并执行。
 */
public class RecordSheetCheck {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        CalibrationDtos.RecordView record = sample();
        String html = RecordSheet.render(record, "齐一");

        check("表头带出记录编号与设备唯一号",
                html.contains("JC20261017091200123") && html.contains("DZTP170911C"));
        check("标题与打印按钮存在",
                html.contains("电子天平校验记录表") && html.contains("打印 / 另存为 PDF"));
        check("九次称量逐行输出", count(html, "<td>R1</td>") == 9);
        check("超差行单独标色", count(html, "<tr class=\"bad\">") == 1);
        check("超差次数与结论一致",
                html.contains("1 / 9") && html.contains("外部校正不通过"));
        check("复核人、复核时间与意见输出",
                html.contains("齐一") && html.contains("2026-10-17 10:30")
                        && html.contains("示值稳定，同意通过"));
        check("备注中的 HTML 被转义",
                !html.contains("<script>alert") && html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        check("导出人写入页脚", html.contains("导出人 齐一"));

        System.out.println();
        System.out.println("结果：" + pass + " 通过，" + fail + " 失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static CalibrationDtos.RecordView sample() {
        CalibrationDtos.RecordView record = new CalibrationDtos.RecordView();
        record.setRecordNo("JC20261017091200123");
        record.setBalanceCode("F001");
        record.setBalanceUniqueNo("DZTP170911C");
        record.setDepartment("化学室");
        record.setCalibrationTime(LocalDateTime.of(2026, 10, 17, 9, 12));
        record.setStatus("REVIEWED");
        record.setExternalResult("否");
        record.setCleanOk(true);
        record.setLevelOk(true);
        record.setInternalCalibrationResult("是");
        record.setInspectorName("李一");
        record.setReviewerName("齐一");
        record.setReviewResult("APPROVED");
        record.setReviewComment("示值稳定，同意通过");
        record.setReviewedAt(LocalDateTime.of(2026, 10, 17, 10, 30));
        record.setRemark("备注含 <script>alert(1)</script> 特殊字符");
        record.setMeasurements(measurements());
        return record;
    }

    private static List<CalibrationMeasurement> measurements() {
        BigDecimal[] nominals = {new BigDecimal("200"), new BigDecimal("50"), new BigDecimal("10")};
        List<CalibrationMeasurement> rows = new ArrayList<>();
        int index = 0;
        for (int weight = 1; weight <= nominals.length; weight++) {
            BigDecimal nominal = nominals[weight - 1];
            for (int measure = 1; measure <= 3; measure++) {
                index++;
                CalibrationMeasurement row = new CalibrationMeasurement();
                row.setRangeSeq(1);
                row.setWeightSeq(weight);
                row.setMeasureSeq(measure);
                row.setNominalG(nominal);
                row.setValueG(measure == 2 && index == 5
                        ? nominal.add(new BigDecimal("0.5"))
                        : nominal);
                row.setDisplayValue(row.getValueG());
                row.setDisplayUnit("g");
                row.setLowerG(nominal.subtract(nominal.multiply(new BigDecimal("0.0005"))));
                row.setUpperG(nominal.add(nominal.multiply(new BigDecimal("0.0005"))));
                row.setMeasuredAt(LocalDateTime.of(2026, 10, 17, 9, 12));
                row.setQualified(!(measure == 2 && index == 5));
                rows.add(row);
            }
        }
        return rows;
    }

    private static int count(String text, String token) {
        int total = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            total++;
            from += token.length();
        }
        return total;
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

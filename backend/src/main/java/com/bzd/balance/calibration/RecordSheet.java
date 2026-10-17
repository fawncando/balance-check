package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 校验记录表页面的渲染：只负责把记录拼成可打印的 A4 HTML，不查库、不判权限。
 *
 * 内网离线环境无法引入 PDF 字体与排版库，因此用浏览器"打印 → 另存为 PDF"替代服务端生成 PDF；
 * 打印样式集中在 @page 与 print 媒体查询里，页面上的工具条不会被打印。
 */
public final class RecordSheet {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private RecordSheet() {
    }

    public static String render(CalibrationDtos.RecordView record, String operatorName) {
        List<CalibrationMeasurement> measurements = record.getMeasurements() == null
                ? List.of() : record.getMeasurements();
        long failed = measurements.stream().filter(row -> Boolean.FALSE.equals(row.getQualified())).count();
        boolean passed = "是".equals(record.getExternalResult());

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n");
        html.append("<title>校验记录表 ").append(escape(record.getRecordNo())).append("</title>\n");
        html.append(style());
        html.append("</head>\n<body>\n");

        html.append("<div class=\"toolbar\">\n")
                .append("<span>校验记录表 · 按 Ctrl+P 或点击右侧按钮打印，目标打印机选择“另存为 PDF”即可归档。</span>\n")
                .append("<button type=\"button\" onclick=\"window.print()\">打印 / 另存为 PDF</button>\n")
                .append("</div>\n");

        html.append("<h1>电子天平校验记录表</h1>\n");
        html.append("<div class=\"meta\">记录编号 ").append(escape(record.getRecordNo())).append("</div>\n");

        html.append("<table class=\"kv\">\n");
        kv(html, "天平编号", str(record.getBalanceCode()), "设备唯一号", str(record.getBalanceUniqueNo()));
        kv(html, "科室", str(record.getDepartment()), "校验时间", format(record.getCalibrationTime(), MINUTE));
        kv(html, "检验人员", str(record.getInspectorName()), "记录状态", statusText(record.getStatus()));
        kv(html, "整机结论", passed ? "外部校正通过" : "外部校正不通过",
                "超差次数", failed + " / " + measurements.size());
        kv(html, "清洁", yesNo(record.getCleanOk()), "水平调节", yesNo(record.getLevelOk()));
        kv(html, "内部校正", str(record.getInternalCalibrationResult()), "复核结论", reviewText(record.getReviewResult()));
        html.append("</table>\n");

        html.append("<table class=\"grid\">\n<thead><tr>")
                .append("<th>序号</th><th>量程</th><th>砝码（g）</th><th>次序</th>")
                .append("<th>示值</th><th>单位</th><th>换算值（g）</th><th>限度（g）</th><th>判定</th>")
                .append("</tr></thead>\n<tbody>\n");
        int index = 1;
        for (CalibrationMeasurement row : measurements) {
            boolean qualified = Boolean.TRUE.equals(row.getQualified());
            html.append("<tr").append(qualified ? "" : " class=\"bad\"").append(">")
                    .append("<td>").append(index++).append("</td>")
                    .append("<td>R").append(row.getRangeSeq()).append("</td>")
                    .append("<td>").append(number(row.getNominalG())).append("</td>")
                    .append("<td>").append(row.getMeasureSeq()).append("</td>")
                    .append("<td>").append(number(row.getDisplayValue())).append("</td>")
                    .append("<td>").append(escape(row.getDisplayUnit())).append("</td>")
                    .append("<td>").append(number(row.getValueG())).append("</td>")
                    .append("<td>").append(number(row.getLowerG())).append(" ~ ")
                    .append(number(row.getUpperG())).append("</td>")
                    .append("<td>").append(qualified ? "合格" : "超差").append("</td>")
                    .append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");

        html.append("<table class=\"kv\">\n");
        kv(html, "复核人员", str(record.getReviewerName()), "复核时间", format(record.getReviewedAt(), MINUTE));
        html.append("<tr><th>复核意见</th><td colspan=\"3\">").append(str(record.getReviewComment())).append("</td></tr>\n");
        html.append("<tr><th>备注</th><td colspan=\"3\">").append(str(record.getRemark())).append("</td></tr>\n");
        html.append("</table>\n");

        html.append("<div class=\"sign\">\n")
                .append("<div>检验人员签名：<span></span></div>\n")
                .append("<div>复核人员签名：<span></span></div>\n")
                .append("<div>日期：<span></span></div>\n")
                .append("</div>\n");

        html.append("<div class=\"foot\">天平校验视频分析系统 · 导出人 ")
                .append(escape(operatorName))
                .append(" · 导出于 ").append(LocalDateTime.now().format(STAMP))
                .append("</div>\n");
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private static void kv(StringBuilder html, String k1, String v1, String k2, String v2) {
        html.append("<tr><th>").append(k1).append("</th><td>").append(v1)
                .append("</td><th>").append(k2).append("</th><td>").append(v2)
                .append("</td></tr>\n");
    }

    private static String style() {
        return """
                <style>
                  @page { size: A4 portrait; margin: 14mm 12mm; }
                  * { box-sizing: border-box; }
                  body { margin: 0; padding: 18px 22px 26px; color: #1f2a44;
                         font-family: "Microsoft YaHei", "SimSun", sans-serif; font-size: 13px; }
                  h1 { font-size: 20px; text-align: center; margin: 6px 0 4px; letter-spacing: 2px; }
                  .meta { text-align: center; font-size: 12px; color: #5c6b87; margin-bottom: 14px; }
                  table { width: 100%; border-collapse: collapse; }
                  .kv { margin-bottom: 14px; }
                  .kv th, .kv td { border: 1px solid #b9c6db; padding: 6px 8px; text-align: left; }
                  .kv th { width: 86px; background: #f2f5fa; color: #43536f; font-weight: 500; }
                  .grid th, .grid td { border: 1px solid #b9c6db; padding: 5px 6px; text-align: center; }
                  .grid th { background: #f2f5fa; color: #43536f; font-weight: 500; }
                  .grid tr.bad td { background: #fdf1f0; color: #b03a2e; }
                  .sign { display: flex; gap: 40px; margin-top: 22px; }
                  .sign span { display: inline-block; border-bottom: 1px solid #8a97ad; width: 150px; height: 16px; }
                  .foot { margin-top: 18px; font-size: 11.5px; color: #6d7a90; border-top: 1px solid #dfe5ee; padding-top: 8px; }
                  .toolbar { display: flex; align-items: center; justify-content: space-between;
                             gap: 16px; background: #eef3fb; border: 1px solid #b9c6db; border-radius: 6px;
                             padding: 8px 14px; margin-bottom: 16px; font-size: 12.5px; color: #43536f; }
                  .toolbar button { font: inherit; padding: 5px 14px; border-radius: 4px; border: 1px solid #1f4e8c;
                                    background: #1f4e8c; color: #fff; cursor: pointer; }
                  @media print {
                    body { padding: 0; }
                    .toolbar { display: none; }
                  }
                </style>
                """;
    }

    private static String statusText(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "待复核";
            case "REVIEWED" -> "已复核";
            default -> status;
        };
    }

    private static String reviewText(String result) {
        if (result == null) {
            return "未复核";
        }
        return "APPROVED".equals(result) ? "通过" : "退回";
    }

    private static String yesNo(Boolean value) {
        if (value == null) {
            return "-";
        }
        return value ? "是" : "否";
    }

    private static String number(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static String format(LocalDateTime time, DateTimeFormatter formatter) {
        return time == null ? "-" : time.format(formatter);
    }

    private static String str(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return escape(value);
    }

    private static String escape(String value) {
        String text = value == null ? "" : value;
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

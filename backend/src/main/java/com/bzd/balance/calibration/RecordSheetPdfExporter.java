package com.bzd.balance.calibration;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;

/**
 * Server-side A4 PDF export. The page is rendered through Java2D so Chinese
 * text uses the workstation font and no additional PDF font dependency is
 * required. The rendered page is embedded as a JPEG image in a standard PDF.
 */
@Component
public class RecordSheetPdfExporter {

    private static final int WIDTH = 1654;
    private static final int HEIGHT = 2339;
    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CalibrationService calibrationService;

    public RecordSheetPdfExporter(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    public PdfExport export(Long id, SessionUser user) {
        if (id == null) {
            throw new BizException("请指定要导出的记录编号");
        }
        if (!user.isReviewer() && !user.isAdmin()) {
            throw new BizException(403, "只有复核确认人员可以导出记录表");
        }
        CalibrationDtos.RecordView record = calibrationService.get(id, user);
        try {
            byte[] jpeg = renderJpeg(record, user.getDisplayName());
            return new PdfExport(record.getRecordNo() + ".pdf", wrapPdf(jpeg));
        } catch (IOException ex) {
            throw new BizException(500, "生成 PDF 失败：" + ex.getMessage());
        }
    }

    private byte[] renderJpeg(CalibrationDtos.RecordView record, String operator)
            throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            Font title = new Font("Microsoft YaHei", Font.BOLD, 42);
            Font heading = new Font("Microsoft YaHei", Font.BOLD, 24);
            Font body = new Font("Microsoft YaHei", Font.PLAIN, 20);
            Font small = new Font("Microsoft YaHei", Font.PLAIN, 17);

            int margin = 92;
            int y = 100;
            graphics.setColor(new Color(31, 42, 68));
            drawCentered(graphics, "电子天平校验记录表", title, WIDTH / 2, y);
            y += 48;
            drawCentered(graphics, "记录编号 " + safe(record.getRecordNo()),
                    small, WIDTH / 2, y);
            y += 50;

            graphics.setFont(body);
            drawMeta(graphics, margin, y, 420,
                    "天平编号", safe(record.getBalanceCode()),
                    "设备唯一号", safe(record.getBalanceUniqueNo()));
            drawMeta(graphics, margin, y + 44, 420,
                    "科室", safe(record.getDepartment()),
                    "校验时间", format(record.getCalibrationTime()));
            drawMeta(graphics, margin, y + 88, 420,
                    "检验人员", safe(record.getInspectorName()),
                    "记录状态", statusText(record.getStatus()));
            drawMeta(graphics, margin, y + 132, 420,
                    "外部校正", "是".equals(record.getExternalResult()) ? "通过" : "不通过",
                    "清洁/水平", yesNo(record.getCleanOk()) + " / " + yesNo(record.getLevelOk()));
            drawMeta(graphics, margin, y + 176, 420,
                    "内部校正", safe(record.getInternalCalibrationResult()),
                    "复核结论", reviewText(record.getReviewResult()));
            y += 240;

            graphics.setFont(heading);
            graphics.setColor(new Color(31, 78, 140));
            graphics.drawString("九次称量明细", margin, y);
            y += 24;

            int tableTop = y;
            int tableLeft = margin;
            int tableWidth = WIDTH - margin * 2;
            int rowHeight = 48;
            int[] widths = {80, 100, 130, 80, 180, 90, 210, 430, 170};
            String[] headers = {
                "序号", "量程", "砝码(g)", "次序", "示值", "单位", "换算值(g)", "限度(g)", "判定"
            };
            graphics.setColor(new Color(242, 245, 250));
            graphics.fillRect(tableLeft, tableTop, tableWidth, rowHeight);
            graphics.setColor(new Color(90, 105, 135));
            graphics.drawRect(tableLeft, tableTop, tableWidth,
                    rowHeight * 10);
            graphics.setFont(new Font("Microsoft YaHei", Font.BOLD, 17));
            int x = tableLeft;
            for (int i = 0; i < widths.length; i++) {
                drawCell(graphics, headers[i], x, tableTop, widths[i], rowHeight);
                x += widths[i];
            }

            List<CalibrationMeasurement> rows = record.getMeasurements() == null
                    ? List.of() : record.getMeasurements();
            graphics.setFont(small);
            for (int row = 0; row < 9; row++) {
                int cellY = tableTop + rowHeight * (row + 1);
                if (row % 2 == 1) {
                    graphics.setColor(new Color(250, 252, 255));
                    graphics.fillRect(tableLeft, cellY, tableWidth, rowHeight);
                }
                CalibrationMeasurement item = row < rows.size() ? rows.get(row) : null;
                String[] cells = item == null
                        ? new String[] {"-", "-", "-", "-", "-", "-", "-", "-", "-"}
                        : new String[] {
                            String.valueOf(row + 1),
                            "R" + item.getRangeSeq(),
                            number(item.getNominalG()),
                            String.valueOf(item.getMeasureSeq()),
                            number(item.getDisplayValue()),
                            safe(item.getDisplayUnit()),
                            number(item.getValueG()),
                            number(item.getLowerG()) + " ~ " + number(item.getUpperG()),
                            Boolean.TRUE.equals(item.getQualified()) ? "合格" : "超差"
                        };
                if (item != null && !Boolean.TRUE.equals(item.getQualified())) {
                    graphics.setColor(new Color(253, 241, 240));
                    graphics.fillRect(tableLeft, cellY, tableWidth, rowHeight);
                }
                graphics.setColor(new Color(185, 198, 219));
                graphics.drawRect(tableLeft, cellY, tableWidth, rowHeight);
                graphics.setColor(item != null && Boolean.TRUE.equals(item.getQualified())
                        ? new Color(31, 42, 68) : new Color(176, 58, 46));
                int cellX = tableLeft;
                for (int col = 0; col < widths.length; col++) {
                    drawCell(graphics, cells[col], cellX, cellY, widths[col], rowHeight);
                    graphics.drawLine(cellX, cellY, cellX, cellY + rowHeight);
                    cellX += widths[col];
                }
            }
            y = tableTop + rowHeight * 10 + 52;

            graphics.setFont(body);
            graphics.setColor(new Color(31, 42, 68));
            drawLabelValue(graphics, margin, y, "复核人员", reviewer(record));
            drawLabelValue(graphics, WIDTH / 2, y, "复核时间",
                    format(record.getReviewedAt()));
            y += 44;
            drawLabelValue(graphics, margin, y, "复核意见", safe(record.getReviewComment()));
            y += 44;
            drawLabelValue(graphics, margin, y, "备注", safe(record.getRemark()));
            if ("VOIDED".equals(record.getStatus())) {
                y += 44;
                drawLabelValue(graphics, margin, y, "作废人员", reviewerVoid(record));
                drawLabelValue(graphics, WIDTH / 2, y, "作废时间", format(record.getVoidedAt()));
                y += 44;
                drawLabelValue(graphics, margin, y, "作废原因", safe(record.getVoidReason()));
            }
            y += 94;

            graphics.setFont(body);
            graphics.drawString("检验人员签名：________________", margin, y);
            graphics.drawString("复核人员签名：________________", margin + 650, y);
            y += 55;
            graphics.drawString("日期：________________", margin, y);

            graphics.setFont(small);
            graphics.setColor(new Color(100, 116, 145));
            graphics.drawString("天平校验视频分析系统 · 导出人 " + safe(operator)
                    + " · 导出于 " + LocalDateTime.now().format(STAMP),
                    margin, HEIGHT - 64);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        image.flush();
        return output.toByteArray();
    }

    private byte[] wrapPdf(byte[] jpeg) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        java.util.List<Integer> offsets = new java.util.ArrayList<>();
        write(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        int obj1 = beginObject(out, offsets);
        write(out, "<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        int obj2 = beginObject(out, offsets);
        write(out, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        int obj3 = beginObject(out, offsets);
        write(out, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /XObject << /Im0 5 0 R >> >> "
                + "/Contents 4 0 R >>\nendobj\n");
        int obj4 = beginObject(out, offsets);
        byte[] content = "q\n595 0 0 842 0 0 cm\n/Im0 Do\nQ\n"
                .getBytes(StandardCharsets.US_ASCII);
        write(out, "<< /Length " + content.length + " >>\nstream\n");
        out.write(content);
        write(out, "endstream\nendobj\n");
        int obj5 = beginObject(out, offsets);
        write(out, "<< /Type /XObject /Subtype /Image /Width " + WIDTH
                + " /Height " + HEIGHT + " /ColorSpace /DeviceRGB "
                + "/BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpeg.length + " >>\nstream\n");
        out.write(jpeg);
        write(out, "\nendstream\nendobj\n");
        int xref = out.size();
        write(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"
                + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private int beginObject(ByteArrayOutputStream out, List<Integer> offsets)
            throws IOException {
        int id = offsets.size() + 1;
        offsets.add(out.size());
        write(out, id + " 0 obj\n");
        return id;
    }

    private void write(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void drawMeta(Graphics2D graphics, int x, int y, int labelWidth,
                          String k1, String v1, String k2, String v2) {
        graphics.setColor(new Color(92, 107, 135));
        graphics.drawString(k1 + "：", x, y);
        graphics.setColor(new Color(31, 42, 68));
        graphics.drawString(v1, x + labelWidth / 2, y);
        graphics.setColor(new Color(92, 107, 135));
        graphics.drawString(k2 + "：", x + labelWidth, y);
        graphics.setColor(new Color(31, 42, 68));
        graphics.drawString(v2, x + labelWidth + labelWidth / 2, y);
    }

    private void drawLabelValue(Graphics2D graphics, int x, int y,
                                String label, String value) {
        graphics.setColor(new Color(92, 107, 135));
        graphics.drawString(label + "：", x, y);
        graphics.setColor(new Color(31, 42, 68));
        graphics.drawString(value, x + 120, y);
    }

    private void drawCentered(Graphics2D graphics, String text, Font font,
                              int centerX, int y) {
        graphics.setFont(font);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, y);
    }

    private void drawCell(Graphics2D graphics, String text, int x, int y,
                          int width, int height) {
        FontMetrics metrics = graphics.getFontMetrics();
        String value = text == null ? "" : text;
        while (metrics.stringWidth(value) > width - 10 && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        int textX = x + Math.max(5, (width - metrics.stringWidth(value)) / 2);
        int textY = y + (height + metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.drawString(value, textX, textY);
    }

    private String number(java.math.BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String yesNo(Boolean value) {
        return value == null ? "-" : value ? "是" : "否";
    }

    private String format(LocalDateTime value) {
        return value == null ? "-" : value.format(MINUTE);
    }

    private String statusText(String value) {
        return switch (value == null ? "" : value) {
            case "DRAFT" -> "草稿";
            case "SUBMITTED" -> "待复核";
            case "REVIEWED" -> "已复核";
            case "VOIDED" -> "已作废";
            default -> safe(value);
        };
    }

    private String reviewText(String value) {
        if (value == null) {
            return "未复核";
        }
        return "APPROVED".equals(value) ? "通过" : "退回";
    }

    private String reviewer(CalibrationDtos.RecordView record) {
        return record.getReviewerName() == null ? "-" : record.getReviewerName();
    }

    private String reviewerVoid(CalibrationDtos.RecordView record) {
        return record.getVoidedByName() == null ? "-" : record.getVoidedByName();
    }

    public record PdfExport(String filename, byte[] content) {
    }
}

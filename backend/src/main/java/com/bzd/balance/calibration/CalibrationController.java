package com.bzd.balance.calibration;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/calibrations")
public class CalibrationController {

    private final CalibrationService calibrationService;
    private final ReviewService reviewService;
    private final RecordSheetExporter recordSheetExporter;
    private final RecordSheetPdfExporter recordSheetPdfExporter;

    public CalibrationController(CalibrationService calibrationService,
                                 ReviewService reviewService,
                                 RecordSheetExporter recordSheetExporter,
                                 RecordSheetPdfExporter recordSheetPdfExporter) {
        this.calibrationService = calibrationService;
        this.reviewService = reviewService;
        this.recordSheetExporter = recordSheetExporter;
        this.recordSheetPdfExporter = recordSheetPdfExporter;
    }

    @PostMapping
    public ApiResponse<CalibrationDtos.RecordView> create(@RequestBody CalibrationDtos.SaveRequest request,
                                                          HttpSession session,
                                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.create(request, user, http.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    public ApiResponse<CalibrationDtos.RecordView> update(@PathVariable Long id,
                                                          @RequestBody CalibrationDtos.SaveRequest request,
                                                          HttpSession session,
                                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.update(id, request, user, http.getRemoteAddr()));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<CalibrationDtos.RecordView> submit(@PathVariable Long id,
                                                          HttpSession session,
                                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.submit(id, user, http.getRemoteAddr()));
    }

    /** 复核：通过则锁定记录，退回则回到草稿。 */
    @PostMapping("/{id}/review")
    public ApiResponse<CalibrationDtos.RecordView> review(@PathVariable Long id,
                                                          @RequestBody CalibrationDtos.ReviewRequest request,
                                                          HttpSession session,
                                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        reviewService.review(id, request, user, http.getRemoteAddr());
        return ApiResponse.ok(calibrationService.get(id, user));
    }

    /** 复核人员解锁已复核记录，交回检验人员修改。 */
    @PostMapping("/{id}/reopen")
    public ApiResponse<CalibrationDtos.RecordView> reopen(@PathVariable Long id,
                                                          HttpSession session,
                                                          HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        reviewService.reopen(id, user, http.getRemoteAddr());
        return ApiResponse.ok(calibrationService.get(id, user));
    }

    /** 管理员作废记录：保留数据与审计日志，作废后不可再编辑或复核。 */
    @PostMapping("/{id}/void")
    public ApiResponse<CalibrationDtos.RecordView> voidRecord(
            @PathVariable Long id,
            @RequestBody CalibrationDtos.VoidRequest request,
            HttpSession session,
            HttpServletRequest http) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.voidRecord(
                id, request, user, http.getRemoteAddr()));
    }

    @GetMapping("/{id}")
    public ApiResponse<CalibrationDtos.RecordView> get(@PathVariable Long id, HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.get(id, user));
    }

    /** 记录查询：按天平、状态、记录编号与日期段过滤；传 pageSize 时按页返回，总数放在 X-Total-Count。 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CalibrationDtos.RecordView>>> list(
            CalibrationDtos.Query query, HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        CalibrationService.Page page = calibrationService.list(query, user);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(page.total()))
                .body(ApiResponse.ok(page.items()));
    }

    /**
     * 记录表导出：返回可直接打印（另存为 PDF）的 A4 页面。
     * 这里显式设置 Content-Type 而不在映射上声明 produces，出错时才能正常走 JSON 错误响应。
     */
    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(value = "id", required = false) Long id,
                                         HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        String html = recordSheetExporter.render(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .body(html);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id,
                                            HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        RecordSheetPdfExporter.PdfExport pdf =
                recordSheetPdfExporter.export(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.filename() + "\"")
                .body(pdf.content());
    }
}

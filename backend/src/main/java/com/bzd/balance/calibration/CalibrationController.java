package com.bzd.balance.calibration;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public CalibrationController(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
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

    @GetMapping("/{id}")
    public ApiResponse<CalibrationDtos.RecordView> get(@PathVariable Long id, HttpSession session) {
        CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.get(id));
    }

    @GetMapping
    public ApiResponse<List<CalibrationDtos.RecordView>> list(HttpSession session) {
        CurrentUser.require(session);
        return ApiResponse.ok(calibrationService.list());
    }
}

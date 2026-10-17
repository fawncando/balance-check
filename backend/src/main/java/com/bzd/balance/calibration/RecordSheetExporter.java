package com.bzd.balance.calibration;

import org.springframework.stereotype.Component;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;

/**
 * 记录表导出入口：校验权限、取出记录，再交给 RecordSheet 渲染成可打印页面。
 */
@Component
public class RecordSheetExporter {

    private final CalibrationService calibrationService;

    public RecordSheetExporter(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    /** 仅复核确认人员与管理员可以导出记录表。 */
    public String render(Long id, SessionUser user) {
        if (id == null) {
            throw new BizException("请指定要导出的记录编号");
        }
        if (!user.isReviewer() && !user.isAdmin()) {
            throw new BizException(403, "只有复核确认人员可以导出记录表");
        }
        CalibrationDtos.RecordView record = calibrationService.get(id, user);
        return RecordSheet.render(record, user.getDisplayName());
    }
}

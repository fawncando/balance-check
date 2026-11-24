package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.balance.Balance;
import com.bzd.balance.balance.BalanceMapper;
import com.bzd.balance.balance.BalanceRange;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.bzd.balance.video.Video;
import com.bzd.balance.video.VideoMapper;

@Service
public class CalibrationService {

    private final CalibrationMapper calibrationMapper;
    private final BalanceMapper balanceMapper;
    private final JudgementService judgementService;
    private final AuditService auditService;
    private final VideoMapper videoMapper;

    public CalibrationService(CalibrationMapper calibrationMapper,
                              BalanceMapper balanceMapper,
                              JudgementService judgementService,
                              AuditService auditService,
                              VideoMapper videoMapper) {
        this.calibrationMapper = calibrationMapper;
        this.balanceMapper = balanceMapper;
        this.judgementService = judgementService;
        this.auditService = auditService;
        this.videoMapper = videoMapper;
    }

    @Transactional
    public CalibrationDtos.RecordView create(CalibrationDtos.SaveRequest request,
                                             SessionUser user,
                                             String ip) {
        ensureCanEdit(user);
        if (request.getBalanceId() == null) {
            throw new BizException("请选择天平");
        }
        Balance balance = balanceMapper.findById(request.getBalanceId());
        if (balance == null) {
            throw new BizException("所选天平不存在");
        }
        if (request.getMeasurements() == null || request.getMeasurements().isEmpty()) {
            throw new BizException("请填写称量数据");
        }
        if (request.getVideoId() != null
                && calibrationMapper.findByVideoId(request.getVideoId()) != null) {
            throw new BizException("该视频已经生成校验记录");
        }
        String manualReason = trimToNull(request.getManualEntryReason());
        if (request.getVideoId() == null) {
            if (manualReason == null || manualReason.length() < 2) {
                throw new BizException("手工补录记录必须填写原因");
            }
            if (manualReason.length() > 500) {
                throw new BizException("手工补录原因不能超过 500 个字符");
            }
        } else {
            Video video = videoMapper.findById(request.getVideoId());
            if (video == null) {
                throw new BizException("关联视频不存在");
            }
            if (!request.getBalanceId().equals(video.getBalanceId())) {
                throw new BizException("视频与天平不匹配");
            }
            if (!user.isAdmin() && !user.getId().equals(video.getUploaderId())) {
                throw new BizException(403, "只能使用自己上传的视频");
            }
        }

        Calibration record = new Calibration();
        record.setRecordNo(nextRecordNo());
        record.setBalanceId(request.getBalanceId());
        record.setVideoId(request.getVideoId());
        record.setInspectorId(user.getId());
        record.setCalibrationTime(request.getCalibrationTime() != null
                ? request.getCalibrationTime()
                : LocalDateTime.now().withSecond(0).withNano(0));
        record.setCleanOk(request.getCleanOk());
        record.setLevelOk(request.getLevelOk());
        record.setInternalCalibrationResult(request.getInternalCalibrationResult());
        String remark = blankToDefault(request.getRemark());
        record.setRemark(request.getVideoId() == null
                ? limitText("【手工补录：" + manualReason + "】" + ("无".equals(remark) ? "" : remark), 500)
                : limitText(remark, 500));
        record.setStatus("DRAFT");

        List<CalibrationMeasurement> measurements =
                buildMeasurements(record, request.getMeasurements());
        calibrationMapper.insert(record);
        persistMeasurements(record.getId(), measurements);
        auditService.log(user.getId(),
                request.getVideoId() == null
                        ? "CALIBRATION_CREATE_MANUAL" : "CALIBRATION_CREATE",
                "CALIBRATION", String.valueOf(record.getId()),
                request.getVideoId() == null
                        ? record.getRecordNo() + "：" + manualReason
                        : record.getRecordNo(),
                ip);
        return get(record.getId(), user);
    }

    @Transactional
    public CalibrationDtos.RecordView update(Long id,
                                             CalibrationDtos.SaveRequest request,
                                             SessionUser user,
                                             String ip) {
        Calibration record = requireRecord(id);
        ensureCanEdit(user);
        if (!record.getInspectorId().equals(user.getId()) && !user.isAdmin()) {
            throw new BizException(403, "只能修改自己创建的记录");
        }
        if (!"DRAFT".equals(record.getStatus())) {
            throw new BizException("已提交的记录不能修改");
        }
        if (request.getMeasurements() == null || request.getMeasurements().isEmpty()) {
            throw new BizException("请填写称量数据");
        }

        record.setCalibrationTime(request.getCalibrationTime() != null
                ? request.getCalibrationTime()
                : record.getCalibrationTime());
        record.setCleanOk(request.getCleanOk());
        record.setLevelOk(request.getLevelOk());
        record.setInternalCalibrationResult(request.getInternalCalibrationResult());
        record.setRemark(blankToDefault(request.getRemark()));

        List<CalibrationMeasurement> measurements =
                buildMeasurements(record, request.getMeasurements());
        calibrationMapper.updateDraft(record);
        calibrationMapper.deleteMeasurements(id);
        persistMeasurements(id, measurements);
        auditService.log(user.getId(), "CALIBRATION_UPDATE", "CALIBRATION",
                String.valueOf(id), record.getRecordNo(), ip);
        return get(id, user);
    }

    public CalibrationDtos.RecordView submit(Long id, SessionUser user, String ip) {
        Calibration record = requireRecord(id);
        ensureCanEdit(user);
        if (!record.getInspectorId().equals(user.getId()) && !user.isAdmin()) {
            throw new BizException(403, "只能提交自己创建的记录");
        }
        if (!"DRAFT".equals(record.getStatus())) {
            throw new BizException("只有草稿可以提交");
        }
        if (calibrationMapper.submit(id) == 0) {
            throw new BizException("记录状态已变化，请刷新后重试");
        }
        auditService.log(user.getId(), "CALIBRATION_SUBMIT", "CALIBRATION",
                String.valueOf(id), record.getRecordNo(), ip);
        return get(id, user);
    }

    @Transactional
    public CalibrationDtos.RecordView voidRecord(Long id,
                                                 CalibrationDtos.VoidRequest request,
                                                 SessionUser user,
                                                 String ip) {
        if (!user.isAdmin()) {
            throw new BizException(403, "只有管理员可以作废校验记录");
        }
        Calibration record = requireRecord(id);
        if ("VOIDED".equals(record.getStatus())) {
            throw new BizException("该记录已经作废");
        }
        String reason = request == null ? null : trimToNull(request.getReason());
        if (reason == null || reason.length() < 2) {
            throw new BizException("作废必须填写明确原因");
        }
        if (reason.length() > 500) {
            throw new BizException("作废原因不能超过 500 个字符");
        }
        if (calibrationMapper.voidRecord(id, user.getId(), reason) == 0) {
            throw new BizException("记录状态已变化，请刷新后重试");
        }
        auditService.log(user.getId(), "CALIBRATION_VOID", "CALIBRATION",
                String.valueOf(id), record.getRecordNo() + "：" + reason, ip);
        return get(id, user);
    }

    /** 记录详情：检验人员只能查看自己创建的记录，复核确认人员与管理员可以查看全部。 */
    public CalibrationDtos.RecordView get(Long id, SessionUser user) {
        CalibrationDtos.RecordView view = calibrationMapper.findView(id);
        if (view == null) {
            throw new BizException(404, "记录不存在");
        }
        if (user.isInspector() && !user.getId().equals(view.getInspectorId())) {
            throw new BizException(403, "只能查看自己创建的记录");
        }
        view.setMeasurements(calibrationMapper.findMeasurements(id));
        return view;
    }

    /** 分页结果：items 为当前页，total 为满足条件的总条数。 */
    public record Page(List<CalibrationDtos.RecordView> items, long total) {
    }

    /** 记录查询：检验人员只看自己的记录，复核确认人员与管理员看全部；pageSize 为空时返回全部。 */
    public Page list(CalibrationDtos.Query query, SessionUser user) {
        CalibrationDtos.Query condition = query == null ? new CalibrationDtos.Query() : query;
        String status = trimToNull(condition.getStatus());
        if (status != null) {
            status = status.toUpperCase(java.util.Locale.ROOT);
            if (!ReviewRules.isKnownStatus(status)) {
                throw new BizException("记录状态取值不合法");
            }
        }
        LocalDate dateFrom = condition.getDateFrom();
        LocalDate dateTo = condition.getDateTo();
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        int page = condition.getPage() == null ? 1 : condition.getPage();
        Integer pageSize = condition.getPageSize();
        if (page < 1) {
            throw new BizException("页码必须大于 0");
        }
        if (pageSize != null && (pageSize < 1 || pageSize > 200)) {
            throw new BizException("每页条数必须在 1 到 200 之间");
        }
        Long inspectorId = user.isInspector() ? user.getId() : null;
        Long balanceId = condition.getBalanceId();
        String recordNo = trimToNull(condition.getRecordNo());
        LocalDateTime timeFrom = dateFrom == null ? null : dateFrom.atStartOfDay();
        LocalDateTime timeTo = dateTo == null ? null : dateTo.plusDays(1).atStartOfDay();
        if (pageSize == null) {
            List<CalibrationDtos.RecordView> items = calibrationMapper.search(
                    inspectorId, balanceId, status, recordNo, timeFrom, timeTo, 0, null);
            return new Page(items, items.size());
        }
        long total = calibrationMapper.countSearch(
                inspectorId, balanceId, status, recordNo, timeFrom, timeTo);
        int offset = (page - 1) * pageSize;
        List<CalibrationDtos.RecordView> items = calibrationMapper.search(
                inspectorId, balanceId, status, recordNo, timeFrom, timeTo, offset, pageSize);
        return new Page(items, total);
    }

    private List<CalibrationMeasurement> buildMeasurements(Calibration record,
                                                           List<CalibrationDtos.MeasurementInput> inputs) {
        Map<Integer, BigDecimal> divisions = new HashMap<>();
        for (BalanceRange range : balanceMapper.findAllRanges()) {
            if (range.getBalanceId().equals(record.getBalanceId())) {
                divisions.put(range.getSeqNo(), range.getDivisionMg());
            }
        }
        boolean allQualified = true;
        List<CalibrationMeasurement> rows = new ArrayList<>();
        for (CalibrationDtos.MeasurementInput input : inputs) {
            int rangeSeq = input.getRangeSeq() == null ? 1 : input.getRangeSeq();
            BigDecimal division = divisions.get(rangeSeq);
            if (division == null) {
                throw new BizException("天平的 " + rangeSeq + " 号量程不存在");
            }
            if (input.getNominalG() == null) {
                throw new BizException("缺少砝码标称值");
            }
            if (input.getDisplayValue() == null) {
                throw new BizException("缺少测量示值");
            }
            int scale = judgementService.scaleForDivision(division);
            BigDecimal valueG = judgementService.toGram(input.getDisplayValue(), input.getDisplayUnit());
            boolean qualified = judgementService.qualified(valueG, input.getNominalG(), scale);
            allQualified &= qualified;

            CalibrationMeasurement row = new CalibrationMeasurement();
            row.setRangeSeq(rangeSeq);
            row.setWeightSeq(input.getWeightSeq());
            row.setMeasureSeq(input.getMeasureSeq());
            row.setNominalG(input.getNominalG());
            row.setLowerG(judgementService.lowerLimit(input.getNominalG(), scale));
            row.setUpperG(judgementService.upperLimit(input.getNominalG(), scale));
            row.setDisplayValue(input.getDisplayValue());
            row.setDisplayUnit(input.getDisplayUnit() == null ? "g" : input.getDisplayUnit());
            row.setValueG(valueG);
            row.setMeasuredAt(input.getMeasuredAt() != null ? input.getMeasuredAt() : record.getCalibrationTime());
            row.setVideoSecond(input.getVideoSecond());
            row.setQualified(qualified);
            row.setConfidence(input.getConfidence());
            row.setManuallyCorrected(Boolean.TRUE.equals(input.getManuallyCorrected()));
            row.setRawText(input.getRawText());
            rows.add(row);
        }
        record.setExternalResult(allQualified ? "是" : "否");
        return rows;
    }

    private void persistMeasurements(Long calibrationId, List<CalibrationMeasurement> rows) {
        for (CalibrationMeasurement row : rows) {
            row.setCalibrationId(calibrationId);
            calibrationMapper.insertMeasurement(row);
        }
    }

    private Calibration requireRecord(Long id) {
        Calibration record = calibrationMapper.findById(id);
        if (record == null) {
            throw new BizException(404, "记录不存在");
        }
        return record;
    }

    private void ensureCanEdit(SessionUser user) {
        if (user.isReviewer()) {
            throw new BizException(403, "复核确认人员不能创建或修改校验记录");
        }
    }

    private String nextRecordNo() {
        return "JC" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private String blankToDefault(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String limitText(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

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

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.balance.Balance;
import com.bzd.balance.balance.BalanceMapper;
import com.bzd.balance.balance.BalanceRange;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;

@Service
public class CalibrationService {

    private final CalibrationMapper calibrationMapper;
    private final BalanceMapper balanceMapper;
    private final JudgementService judgementService;
    private final AuditService auditService;

    public CalibrationService(CalibrationMapper calibrationMapper,
                              BalanceMapper balanceMapper,
                              JudgementService judgementService,
                              AuditService auditService) {
        this.calibrationMapper = calibrationMapper;
        this.balanceMapper = balanceMapper;
        this.judgementService = judgementService;
        this.auditService = auditService;
    }

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
        record.setRemark(blankToDefault(request.getRemark()));
        record.setStatus("DRAFT");

        List<CalibrationMeasurement> measurements =
                buildMeasurements(record, request.getMeasurements());
        calibrationMapper.insert(record);
        persistMeasurements(record.getId(), measurements);
        auditService.log(user.getId(), "CALIBRATION_CREATE", "CALIBRATION",
                String.valueOf(record.getId()), record.getRecordNo(), ip);
        return get(record.getId(), user);
    }

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

    /** 记录查询：检验人员只看自己的记录，复核确认人员与管理员看全部。 */
    public List<CalibrationDtos.RecordView> list(CalibrationDtos.Query query, SessionUser user) {
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
        Long inspectorId = user.isInspector() ? user.getId() : null;
        return calibrationMapper.search(inspectorId, condition.getBalanceId(), status,
                trimToNull(condition.getRecordNo()),
                dateFrom == null ? null : dateFrom.atStartOfDay(),
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay());
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
}

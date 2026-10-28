package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;

/**
 * 天平清单维护：新增与修改基本信息、量程与砝码组配置。
 * 历史校验记录只保存限度值与天平编号，因此量程可以整体替换，不影响已归档记录。
 */
@Service
public class BalanceMaintenanceService {

    private final BalanceMapper balanceMapper;
    private final BalanceService balanceService;
    private final AuditService auditService;

    public BalanceMaintenanceService(BalanceMapper balanceMapper,
                                     BalanceService balanceService,
                                     AuditService auditService) {
        this.balanceMapper = balanceMapper;
        this.balanceService = balanceService;
        this.auditService = auditService;
    }

    public BalanceView create(BalanceRequest request, SessionUser user, String ip) {
        ensureMaintainer(user);
        Balance balance = new Balance();
        List<BalanceRange> ranges = prepare(request, balance, "ACTIVE");
        try {
            balanceMapper.insert(balance);
        } catch (DuplicateKeyException ex) {
            throw new BizException("天平编号或设备唯一号已存在");
        }
        saveRanges(balance.getId(), ranges);
        auditService.log(user.getId(), "BALANCE_CREATE", "BALANCE", String.valueOf(balance.getId()),
                balance.getCode() + " " + balance.getDepartment(), ip);
        return balanceService.get(balance.getId());
    }

    public BalanceView update(Long id, BalanceRequest request, SessionUser user, String ip) {
        ensureMaintainer(user);
        Balance balance = balanceMapper.findById(id);
        if (balance == null) {
            throw new BizException(404, "天平不存在");
        }
        List<BalanceRange> ranges = prepare(request, balance, balance.getStatus());
        try {
            balanceMapper.update(balance);
        } catch (DuplicateKeyException ex) {
            throw new BizException("天平编号或设备唯一号已存在");
        }
        balanceMapper.deleteRanges(id);
        saveRanges(id, ranges);
        auditService.log(user.getId(), "BALANCE_UPDATE", "BALANCE", String.valueOf(id),
                balance.getCode() + " " + balance.getDepartment() + " / " + balance.getStatus(), ip);
        return balanceService.get(id);
    }

    private List<BalanceRange> prepare(BalanceRequest request, Balance balance, String defaultStatus) {
        if (request == null) {
            throw new BizException("请填写天平信息");
        }
        String code = trimToNull(request.getCode());
        String uniqueNo = trimToNull(request.getUniqueNo());
        String department = trimToNull(request.getDepartment());
        String status = trimToNull(request.getStatus()) == null ? defaultStatus : trimToNull(request.getStatus());
        String mode = trimToNull(request.getInternalCalibrationMode());
        String unit = trimToNull(request.getDefaultUnit());

        require(BalanceRules.checkCode(code));
        require(BalanceRules.checkUniqueNo(uniqueNo));
        require(BalanceRules.checkDepartment(department));
        require(BalanceRules.checkStatus(status));
        require(BalanceRules.checkInternalMode(mode));
        require(BalanceRules.checkUnit(unit));
        require(BalanceRules.checkRangeCount(request.getRangeCount()));

        List<BalanceRequest.RangeInput> inputs = request.getRanges() == null ? List.of() : request.getRanges();
        List<BalanceRange> ranges = new ArrayList<>();
        List<Integer> seqNumbers = new ArrayList<>();
        int fallbackSeq = 1;
        for (BalanceRequest.RangeInput input : inputs) {
            int seqNo = input.getSeqNo() == null ? fallbackSeq : input.getSeqNo();
            fallbackSeq = seqNo + 1;
            require(BalanceRules.checkRange(seqNo, input.getMaxCapacityG(), input.getDivisionMg(),
                    input.getWeightSetId()));
            if (balanceMapper.countWeightSet(input.getWeightSetId()) == 0) {
                throw new BizException("所选的砝码组不存在");
            }
            BalanceRange range = new BalanceRange();
            range.setSeqNo(seqNo);
            range.setMaxCapacityG(input.getMaxCapacityG());
            range.setDivisionMg(input.getDivisionMg());
            range.setWeightSetId(input.getWeightSetId());
            ranges.add(range);
            seqNumbers.add(seqNo);
        }
        require(BalanceRules.checkRanges(request.getRangeCount(), seqNumbers));

        balance.setCode(code.trim());
        balance.setUniqueNo(uniqueNo.trim());
        balance.setDepartment(department.trim());
        balance.setRangeCount(request.getRangeCount());
        balance.setInternalCalibrationMode(mode);
        balance.setDefaultUnit(unit);
        balance.setStatus(status);
        return ranges;
    }

    private void saveRanges(Long balanceId, List<BalanceRange> ranges) {
        for (BalanceRange range : ranges) {
            balanceMapper.insertRange(balanceId, range.getSeqNo(), range.getMaxCapacityG(),
                    range.getDivisionMg(), range.getWeightSetId());
        }
    }

    private void ensureMaintainer(SessionUser user) {
        if (!user.isAdmin() && !user.isReviewer()) {
            throw new BizException(403, "只有复核确认人员可以维护天平清单");
        }
    }

    private void require(String error) {
        if (error != null) {
            throw new BizException(error);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

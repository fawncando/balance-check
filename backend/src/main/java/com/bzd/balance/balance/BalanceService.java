package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;

@Service
public class BalanceService {

    private final BalanceMapper balanceMapper;

    public BalanceService(BalanceMapper balanceMapper) {
        this.balanceMapper = balanceMapper;
    }

    public List<BalanceView> listAll() {
        Map<Long, List<BigDecimal>> weightsBySet = weightsBySet();
        Map<Long, List<BalanceRange>> rangesByBalance = new HashMap<>();
        for (BalanceRange range : balanceMapper.findAllRanges()) {
            rangesByBalance.computeIfAbsent(range.getBalanceId(), key -> new ArrayList<>()).add(range);
        }

        List<BalanceView> views = new ArrayList<>();
        for (Balance balance : balanceMapper.findAllActive()) {
            BalanceView view = toView(balance);
            fillRanges(view, rangesByBalance.getOrDefault(balance.getId(), List.of()), weightsBySet);
            views.add(view);
        }
        return views;
    }

    /** 详情：启用与停用的天平都带出量程，维护页编辑停用设备时需要。 */
    public BalanceView get(Long id) {
        Balance balance = balanceMapper.findById(id);
        if (balance == null) {
            throw new BizException(404, "天平不存在");
        }
        BalanceView view = toView(balance);
        fillRanges(view, balanceMapper.findRangesByBalance(id), weightsBySet());
        return view;
    }

    private Map<Long, List<BigDecimal>> weightsBySet() {
        Map<Long, List<BigDecimal>> weightsBySet = new HashMap<>();
        for (WeightItem item : balanceMapper.findAllWeightItems()) {
            weightsBySet.computeIfAbsent(item.getWeightSetId(), key -> new ArrayList<>())
                    .add(item.getNominalG());
        }
        return weightsBySet;
    }

    private void fillRanges(BalanceView view, List<BalanceRange> ranges,
                            Map<Long, List<BigDecimal>> weightsBySet) {
        for (BalanceRange range : ranges) {
            BalanceView.RangeView rangeView = new BalanceView.RangeView();
            rangeView.setSeqNo(range.getSeqNo());
            rangeView.setMaxCapacityG(range.getMaxCapacityG());
            rangeView.setDivisionMg(range.getDivisionMg());
            rangeView.setWeightSetId(range.getWeightSetId());
            rangeView.setWeightSetNo(range.getWeightSetNo());
            rangeView.setWeights(new ArrayList<>(weightsBySet.getOrDefault(range.getWeightSetId(), List.of())));
            view.getRanges().add(rangeView);
        }
    }

    private BalanceView toView(Balance balance) {
        BalanceView view = new BalanceView();
        view.setId(balance.getId());
        view.setCode(balance.getCode());
        view.setUniqueNo(balance.getUniqueNo());
        view.setDepartment(balance.getDepartment());
        view.setRangeCount(balance.getRangeCount());
        view.setInternalCalibrationMode(balance.getInternalCalibrationMode());
        view.setDefaultUnit(balance.getDefaultUnit());
        view.setStatus(balance.getStatus());
        return view;
    }
}

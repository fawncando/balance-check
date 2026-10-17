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
        List<Balance> balances = balanceMapper.findAllActive();
        List<BalanceRange> ranges = balanceMapper.findAllRanges();
        List<WeightItem> items = balanceMapper.findAllWeightItems();

        Map<Long, List<BigDecimal>> weightsBySet = new HashMap<>();
        for (WeightItem item : items) {
            weightsBySet.computeIfAbsent(item.getWeightSetId(), key -> new ArrayList<>())
                    .add(item.getNominalG());
        }
        Map<Long, List<BalanceRange>> rangesByBalance = new HashMap<>();
        for (BalanceRange range : ranges) {
            rangesByBalance.computeIfAbsent(range.getBalanceId(), key -> new ArrayList<>()).add(range);
        }

        List<BalanceView> views = new ArrayList<>();
        for (Balance balance : balances) {
            BalanceView view = toView(balance);
            for (BalanceRange range : rangesByBalance.getOrDefault(balance.getId(), List.of())) {
                BalanceView.RangeView rangeView = new BalanceView.RangeView();
                rangeView.setSeqNo(range.getSeqNo());
                rangeView.setMaxCapacityG(range.getMaxCapacityG());
                rangeView.setDivisionMg(range.getDivisionMg());
                rangeView.setWeightSetNo(range.getWeightSetNo());
                rangeView.setWeights(new ArrayList<>(weightsBySet.getOrDefault(range.getWeightSetId(), List.of())));
                view.getRanges().add(rangeView);
            }
            views.add(view);
        }
        return views;
    }

    public BalanceView get(Long id) {
        Balance balance = balanceMapper.findById(id);
        if (balance == null) {
            throw new BizException(404, "天平不存在");
        }
        return listAll().stream()
                .filter(view -> view.getId().equals(id))
                .findFirst()
                .orElseGet(() -> toView(balance));
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

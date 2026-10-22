package com.bzd.balance.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/** 砝码组查询：天平量程维护时需要选择砝码组。 */
@Service
public class WeightSetService {

    private final BalanceMapper balanceMapper;

    public WeightSetService(BalanceMapper balanceMapper) {
        this.balanceMapper = balanceMapper;
    }

    public List<WeightSetView> listAll() {
        Map<Long, List<BigDecimal>> itemsBySet = new HashMap<>();
        for (WeightItem item : balanceMapper.findAllWeightItems()) {
            itemsBySet.computeIfAbsent(item.getWeightSetId(), key -> new ArrayList<>())
                    .add(item.getNominalG());
        }
        List<WeightSetView> views = new ArrayList<>();
        for (WeightSet set : balanceMapper.findAllWeightSets()) {
            WeightSetView view = new WeightSetView();
            view.setId(set.getId());
            view.setSetNo(set.getSetNo());
            view.setGrade(set.getGrade());
            view.setValidUntil(set.getValidUntil());
            view.setNote(set.getNote());
            view.setWeights(new ArrayList<>(itemsBySet.getOrDefault(set.getId(), List.of())));
            views.add(view);
        }
        return views;
    }
}

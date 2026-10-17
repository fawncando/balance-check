package com.bzd.balance.calibration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 校验判定：限度按标称值的 ±0.05% 计算，示值统一换算为 g 后比较。
 * 判定精度由天平分度值决定，例如 0.1 mg 对应 4 位小数，0.01 mg 对应 5 位小数。
 */
@Service
public class JudgementService {

    private static final BigDecimal LIMIT_RATE = new BigDecimal("0.0005");
    private static final BigDecimal MG_PER_GRAM = new BigDecimal("1000");

    /** 分度值（mg）对应的小数位数：0.1 mg -> 4，0.01 mg -> 5。 */
    public int scaleForDivision(BigDecimal divisionMg) {
        return divisionMg.movePointLeft(3).stripTrailingZeros().scale();
    }

    /** 把天平显示的数值换算为 g；单位以天平显示为准。 */
    public BigDecimal toGram(BigDecimal displayValue, String displayUnit) {
        if (displayValue == null) {
            return null;
        }
        if ("mg".equalsIgnoreCase(displayUnit)) {
            return displayValue.divide(MG_PER_GRAM);
        }
        return displayValue;
    }

    public BigDecimal lowerLimit(BigDecimal nominalG, int scale) {
        return nominalG.subtract(nominalG.multiply(LIMIT_RATE)).setScale(scale, RoundingMode.HALF_UP);
    }

    public BigDecimal upperLimit(BigDecimal nominalG, int scale) {
        return nominalG.add(nominalG.multiply(LIMIT_RATE)).setScale(scale, RoundingMode.HALF_UP);
    }

    /** 单次判定，边界值算合格。 */
    public boolean qualified(BigDecimal valueG, BigDecimal nominalG, int scale) {
        if (valueG == null) {
            return false;
        }
        BigDecimal lower = lowerLimit(nominalG, scale);
        BigDecimal upper = upperLimit(nominalG, scale);
        return valueG.compareTo(lower) >= 0 && valueG.compareTo(upper) <= 0;
    }

    /** 九次称量全部合格才算整机通过。 */
    public boolean overallQualified(List<BigDecimal> valuesG, List<BigDecimal> nominalsG, int scale) {
        if (valuesG == null || nominalsG == null || valuesG.isEmpty()
                || valuesG.size() != nominalsG.size()) {
            return false;
        }
        for (int i = 0; i < valuesG.size(); i++) {
            if (!qualified(valuesG.get(i), nominalsG.get(i), scale)) {
                return false;
            }
        }
        return true;
    }
}

package com.bzd.balance.calibration;

import java.util.Set;

import com.bzd.balance.common.BizException;

/**
 * 复核状态与结论的判定规则，不依赖数据库与 Spring 上下文，便于单独测试。
 *
 * 记录状态按 DRAFT（草稿）→ SUBMITTED（待复核）→ REVIEWED（已复核，锁定）推进；
 * 复核退回时记录回到 DRAFT，复核结论 REJECTED 保留在 review 表中，供检验人员查看退回原因。
 */
public final class ReviewRules {

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_REVIEWED = "REVIEWED";

    private static final Set<String> STATUSES =
            Set.of(STATUS_DRAFT, STATUS_SUBMITTED, STATUS_REVIEWED);

    private ReviewRules() {
    }

    public static boolean isKnownStatus(String status) {
        return status != null && STATUSES.contains(status);
    }

    /** 只有待复核的记录可以复核。 */
    public static boolean canReview(String status) {
        return STATUS_SUBMITTED.equals(status);
    }

    /** 只有已复核（锁定）的记录可以由复核人员解锁。 */
    public static boolean canReopen(String status) {
        return STATUS_REVIEWED.equals(status);
    }

    /** 复核结论只接受 APPROVED 与 REJECTED，大小写与空白不敏感。 */
    public static String normalizeResult(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException("请选择复核结论");
        }
        String result = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!APPROVED.equals(result) && !REJECTED.equals(result)) {
            throw new BizException("复核结论只能是 APPROVED 或 REJECTED");
        }
        return result;
    }

    /** 退回必须写明原因，通过可以不填。 */
    public static boolean commentRequired(String result) {
        return REJECTED.equals(result);
    }

    /** 复核后记录应处于的状态：通过即锁定，退回则回到草稿供检验人员修改。 */
    public static String statusAfterReview(String result) {
        return APPROVED.equals(result) ? STATUS_REVIEWED : STATUS_DRAFT;
    }
}

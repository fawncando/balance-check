package com.bzd.balance.calibration;

import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;

/**
 * 复核：通过即锁定记录，退回则回到草稿并保留复核意见；已锁定的记录由复核人员解锁。
 */
@Service
public class ReviewService {

    private final CalibrationMapper calibrationMapper;
    private final ReviewMapper reviewMapper;
    private final AuditService auditService;

    public ReviewService(CalibrationMapper calibrationMapper,
                         ReviewMapper reviewMapper,
                         AuditService auditService) {
        this.calibrationMapper = calibrationMapper;
        this.reviewMapper = reviewMapper;
        this.auditService = auditService;
    }

    public void review(Long id, CalibrationDtos.ReviewRequest request, SessionUser user, String ip) {
        ensureCanReview(user);
        Calibration record = requireRecord(id);
        if (!ReviewRules.canReview(record.getStatus())) {
            throw new BizException("只有待复核的记录可以复核");
        }
        String result = ReviewRules.normalizeResult(request == null ? null : request.getResult());
        String comment = normalizeComment(request == null ? null : request.getComment());
        if (ReviewRules.commentRequired(result) && comment == null) {
            throw new BizException("退回记录必须填写复核意见");
        }
        if (calibrationMapper.markReviewed(id, ReviewRules.statusAfterReview(result)) == 0) {
            throw new BizException("记录状态已变化，请刷新后重试");
        }
        reviewMapper.upsert(id, user.getId(), result, comment);
        auditService.log(user.getId(), "CALIBRATION_REVIEW", "CALIBRATION", String.valueOf(id),
                ReviewRules.APPROVED.equals(result) ? "复核通过" : "复核退回：" + comment, ip);
    }

    public void reopen(Long id, SessionUser user, String ip) {
        ensureCanReview(user);
        Calibration record = requireRecord(id);
        if (!ReviewRules.canReopen(record.getStatus())) {
            throw new BizException("只有已复核的记录可以解锁");
        }
        if (calibrationMapper.reopen(id) == 0) {
            throw new BizException("记录状态已变化，请刷新后重试");
        }
        auditService.log(user.getId(), "CALIBRATION_REOPEN", "CALIBRATION", String.valueOf(id),
                "复核人解锁记录，退回检验人员修改", ip);
    }

    private void ensureCanReview(SessionUser user) {
        if (!user.isReviewer() && !user.isAdmin()) {
            throw new BizException(403, "只有复核确认人员可以复核记录");
        }
    }

    private Calibration requireRecord(Long id) {
        Calibration record = calibrationMapper.findById(id);
        if (record == null) {
            throw new BizException(404, "记录不存在");
        }
        return record;
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}

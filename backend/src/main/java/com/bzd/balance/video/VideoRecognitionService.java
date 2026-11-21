package com.bzd.balance.video;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.balance.Balance;
import com.bzd.balance.balance.BalanceMapper;
import com.bzd.balance.balance.BalanceService;
import com.bzd.balance.balance.BalanceView;
import com.bzd.balance.calibration.Calibration;
import com.bzd.balance.calibration.CalibrationDtos;
import com.bzd.balance.calibration.CalibrationMapper;
import com.bzd.balance.calibration.CalibrationService;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.bzd.balance.system.StorageService;

@Service
public class VideoRecognitionService {

    private static final BigDecimal LIMIT_RATE = new BigDecimal("0.0005");

    private final VideoMapper videoMapper;
    private final BalanceMapper balanceMapper;
    private final BalanceService balanceService;
    private final CalibrationMapper calibrationMapper;
    private final CalibrationService calibrationService;
    private final RecognitionProcessService recognitionProcessService;
    private final AuditService auditService;
    private final Path uploadRoot;

    public VideoRecognitionService(VideoMapper videoMapper,
                                   BalanceMapper balanceMapper,
                                   BalanceService balanceService,
                                   CalibrationMapper calibrationMapper,
                                   CalibrationService calibrationService,
                                   RecognitionProcessService recognitionProcessService,
                                   AuditService auditService,
                                   StorageService storageService) {
        this.videoMapper = videoMapper;
        this.balanceMapper = balanceMapper;
        this.balanceService = balanceService;
        this.calibrationMapper = calibrationMapper;
        this.calibrationService = calibrationService;
        this.recognitionProcessService = recognitionProcessService;
        this.auditService = auditService;
        this.uploadRoot = storageService.getRoot();
    }

    public VideoView analyze(Long videoId, SessionUser user, String ip) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        if (user.isReviewer()) {
            throw new BizException(403, "复核确认人员不能发起识别并创建校验记录");
        }
        if (!video.getUploaderId().equals(user.getId()) && !user.isAdmin()) {
            throw new BizException(403, "只能识别自己上传的视频");
        }
        Calibration existing = calibrationMapper.findByVideoId(videoId);
        if (existing != null) {
            if (!"ANALYZED".equals(video.getStatus())) {
                videoMapper.updateStatus(videoId, "ANALYZED",
                        "识别完成，已生成记录 " + existing.getRecordNo());
            }
            return videoMapper.findViewById(videoId);
        }
        if ("ANALYZING".equals(video.getStatus())) {
            throw new BizException("视频正在识别中，请稍后刷新");
        }
        if (video.getBalanceId() == null) {
            throw new BizException("视频未关联天平，不能识别");
        }
        Balance balance = balanceMapper.findById(video.getBalanceId());
        if (balance == null) {
            throw new BizException("视频关联的天平不存在");
        }

        Path videoPath = uploadRoot.resolve(video.getRelativePath()).normalize();
        if (!videoPath.startsWith(uploadRoot) || !Files.isRegularFile(videoPath)) {
            throw new BizException("视频文件不存在");
        }
        if (videoMapper.markAnalyzing(videoId) == 0) {
            throw new BizException("视频状态已变化，请刷新后重试");
        }

        try {
            RecognitionResult best = recognizeBest(videoPath, balance.getCode());
            if (best == null || best.getWeighings() == null
                    || best.getWeighings().size() != 9) {
                throw new BizException("未识别出完整的九次称量，请检查视频与天平选择");
            }
            CalibrationDtos.RecordView record = createDraft(video, best, user, ip);
            videoMapper.updateStatus(videoId, "ANALYZED",
                    "识别完成（" + best.getProfile() + "），已生成记录 "
                            + record.getRecordNo());
            auditService.log(user.getId(), "VIDEO_ANALYZE", "VIDEO",
                    String.valueOf(videoId),
                    best.getProfile() + " -> " + record.getRecordNo(), ip);
            return videoMapper.findViewById(videoId);
        } catch (RuntimeException ex) {
            String message = limit(ex.getMessage() == null ? "识别失败" : ex.getMessage(), 500);
            videoMapper.updateStatus(videoId, "FAILED", message);
            auditService.log(user.getId(), "VIDEO_ANALYZE_FAILED", "VIDEO",
                    String.valueOf(videoId), message, ip);
            VideoView failed = videoMapper.findViewById(videoId);
            if (failed == null) {
                throw ex;
            }
            return failed;
        }
    }

    private RecognitionResult recognizeBest(Path videoPath, String balanceCode) {
        List<String> profiles;
        if ("F001".equalsIgnoreCase(balanceCode)) {
            profiles = List.of("f001");
        } else if ("F002".equalsIgnoreCase(balanceCode)) {
            profiles = List.of("f002a", "f002b");
        } else {
            throw new BizException("暂不支持天平型号 " + balanceCode);
        }

        List<RecognitionResult> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String profile : profiles) {
            try {
                candidates.add(recognitionProcessService.run(videoPath, profile));
            } catch (RuntimeException ex) {
                errors.add(profile + "：" + ex.getMessage());
            }
        }
        RecognitionResult best = candidates.stream()
                .min(Comparator
                        .comparingInt(this::readingCount).reversed()
                        .thenComparingDouble(this::fitPenalty)
                        .thenComparing(RecognitionResult::getProfile))
                .orElse(null);
        if (best == null && !errors.isEmpty()) {
            throw new BizException("视频识别失败：" + String.join("；", errors));
        }
        return best;
    }

    private CalibrationDtos.RecordView createDraft(Video video,
                                                   RecognitionResult result,
                                                   SessionUser user,
                                                   String ip) {
        BalanceView balance = balanceService.get(video.getBalanceId());
        LocalDateTime calibrationTime = video.getUploadedAt() == null
                ? LocalDateTime.now().withSecond(0).withNano(0)
                : video.getUploadedAt().withSecond(0).withNano(0);

        CalibrationDtos.SaveRequest request = new CalibrationDtos.SaveRequest();
        request.setBalanceId(video.getBalanceId());
        request.setVideoId(video.getId());
        request.setCalibrationTime(calibrationTime);
        request.setRemark("视频自动识别生成，请人工确认清洁、水平调节与过程信息");
        request.setMeasurements(toMeasurements(balance, result, calibrationTime));
        return calibrationService.create(request, user, ip);
    }

    private List<CalibrationDtos.MeasurementInput> toMeasurements(
            BalanceView balance,
            RecognitionResult result,
            LocalDateTime calibrationTime) {
        List<CalibrationDtos.MeasurementInput> inputs = new ArrayList<>();
        for (RecognitionResult.Weighing weighing : result.getWeighings()) {
            BalanceView.RangeView range = findRange(balance, weighing.getNominalG());
            boolean displayMg = "f002b".equals(result.getProfile())
                    && range.getSeqNo() > 1;
            int scale = range.getDivisionMg().movePointLeft(3)
                    .stripTrailingZeros().scale();
            BigDecimal displayValue = displayMg
                    ? weighing.getValueG().movePointRight(3)
                    : weighing.getValueG();
            displayValue = displayValue.setScale(scale, RoundingMode.HALF_UP);

            CalibrationDtos.MeasurementInput input =
                    new CalibrationDtos.MeasurementInput();
            input.setRangeSeq(range.getSeqNo());
            input.setWeightSeq(weighing.getWeightSeq());
            input.setMeasureSeq(weighing.getMeasureSeq());
            input.setNominalG(weighing.getNominalG());
            input.setDisplayValue(displayValue);
            input.setDisplayUnit(displayMg ? "mg" : "g");
            input.setMeasuredAt(calibrationTime.plusSeconds(weighing.getStartSec())
                    .withSecond(0).withNano(0));
            input.setVideoSecond(weighing.getStartSec());
            input.setManuallyCorrected(false);
            input.setRawText(displayValue.stripTrailingZeros().toPlainString()
                    + " " + (displayMg ? "mg" : "g"));
            inputs.add(input);
        }
        return inputs;
    }

    private BalanceView.RangeView findRange(BalanceView balance, BigDecimal nominal) {
        return balance.getRanges().stream()
                .filter(range -> range.getMaxCapacityG().compareTo(nominal) >= 0)
                .filter(range -> range.getWeights().stream()
                        .anyMatch(weight -> weight.compareTo(nominal) == 0))
                .min(Comparator.comparing(BalanceView.RangeView::getMaxCapacityG))
                .orElseThrow(() -> new BizException(
                        "天平配置中找不到 " + nominal.toPlainString() + " g 砝码"));
    }

    private int readingCount(RecognitionResult result) {
        return result.getWeighings() == null ? 0 : result.getWeighings().size();
    }

    private double fitPenalty(RecognitionResult result) {
        if (result.getWeighings() == null) {
            return Double.MAX_VALUE;
        }
        double penalty = 0;
        for (RecognitionResult.Weighing weighing : result.getWeighings()) {
            if (weighing.getNominalG() == null || weighing.getValueG() == null
                    || weighing.getNominalG().signum() == 0) {
                return Double.MAX_VALUE;
            }
            BigDecimal diff = weighing.getValueG().subtract(weighing.getNominalG()).abs();
            BigDecimal allowed = weighing.getNominalG().multiply(LIMIT_RATE);
            double ratio = allowed.signum() == 0
                    ? 0
                    : diff.doubleValue() / allowed.doubleValue();
            penalty += ratio;
        }
        return penalty;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

package com.bzd.balance.video;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
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
import com.bzd.balance.user.User;
import com.bzd.balance.user.UserMapper;

@Service
public class VideoRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(VideoRecognitionService.class);

    private static final BigDecimal LIMIT_RATE = new BigDecimal("0.0005");

    private final VideoMapper videoMapper;
    private final BalanceMapper balanceMapper;
    private final BalanceService balanceService;
    private final CalibrationMapper calibrationMapper;
    private final CalibrationService calibrationService;
    private final RecognitionProcessService recognitionProcessService;
    private final AuditService auditService;
    private final RecognitionProperties recognitionProperties;
    private final TaskExecutor recognitionExecutor;
    private final UserMapper userMapper;
    private final Path uploadRoot;
    private final boolean deleteSourceAfterSuccess;
    private final ExecutorService profileExecutor;

    public VideoRecognitionService(VideoMapper videoMapper,
                                   BalanceMapper balanceMapper,
                                   BalanceService balanceService,
                                   CalibrationMapper calibrationMapper,
                                   CalibrationService calibrationService,
                                   RecognitionProcessService recognitionProcessService,
                                   AuditService auditService,
                                   RecognitionProperties recognitionProperties,
                                   @Qualifier("recognitionExecutor") TaskExecutor recognitionExecutor,
                                   UserMapper userMapper,
                                   StorageService storageService,
                                   @org.springframework.beans.factory.annotation.Value(
                                           "${app.video.delete-source-after-success:true}")
                                   boolean deleteSourceAfterSuccess,
                                   @org.springframework.beans.factory.annotation.Value(
                                           "${app.recognition.profile-parallelism:3}")
                                   int profileParallelism) {
        this.videoMapper = videoMapper;
        this.balanceMapper = balanceMapper;
        this.balanceService = balanceService;
        this.calibrationMapper = calibrationMapper;
        this.calibrationService = calibrationService;
        this.recognitionProcessService = recognitionProcessService;
        this.auditService = auditService;
        this.recognitionProperties = recognitionProperties;
        this.recognitionExecutor = recognitionExecutor;
        this.userMapper = userMapper;
        this.uploadRoot = storageService.getRoot();
        this.deleteSourceAfterSuccess = deleteSourceAfterSuccess;
        int poolSize = Math.max(1, profileParallelism);
        this.profileExecutor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "recognition-profile");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    public void shutdownProfileExecutor() {
        profileExecutor.shutdownNow();
    }

    public VideoView startAnalysis(Long videoId, SessionUser user, String ip) {
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
        if (balanceMapper.findById(video.getBalanceId()) == null) {
            throw new BizException("视频关联的天平不存在");
        }

        if (video.getRelativePath() == null || video.getRelativePath().isBlank()) {
            throw new BizException("源视频已清理，如需重新识别请重新上传");
        }
        Path videoPath = uploadRoot.resolve(video.getRelativePath()).normalize();
        if (!videoPath.startsWith(uploadRoot) || !Files.isRegularFile(videoPath)) {
            throw new BizException("视频文件不存在");
        }
        if (videoMapper.markAnalyzing(videoId) == 0) {
            throw new BizException("视频状态已变化，请刷新后重试");
        }

        try {
            recognitionExecutor.execute(() -> runAnalysis(videoId, user, ip));
        } catch (RuntimeException ex) {
            String message = "识别任务繁忙，请稍后重新识别";
            videoMapper.updateStatus(videoId, "FAILED", message);
            auditService.log(user.getId(), "VIDEO_ANALYZE_FAILED", "VIDEO",
                    String.valueOf(videoId), message, ip);
        }
        return videoMapper.findViewById(videoId);
    }

    private void runAnalysis(Long videoId, SessionUser user, String ip) {
        try {
            Video video = videoMapper.findById(videoId);
            Balance balance = balanceMapper.findById(video.getBalanceId());
            Path videoPath = uploadRoot.resolve(video.getRelativePath()).normalize();
            BalanceView balanceView = balanceService.get(balance.getId());
            List<java.math.BigDecimal> nominals = balanceView.getRanges().stream()
                    .flatMap(range -> range.getWeights().stream())
                    .distinct()
                    .sorted()
                    .toList();
            RecognitionResult best = recognizeBest(videoPath, balance.getCode(), nominals);
            int found = best == null || best.getWeighings() == null
                    ? 0 : best.getWeighings().size();
            if (found != 9) {
                throw new BizException(RecognitionMessages.incompleteWeighings(found, 9));
            }
            if (best.isConfidenceBelow(recognitionProperties.getMinimumConfidence())) {
                throw new BizException(RecognitionMessages.lowConfidence(
                        best.getConfidence(),
                        recognitionProperties.getMinimumConfidence()));
            }

            String card = best.getPersonCard();
            User account = userMapper.findById(user.getId());
            String expectedCard = account == null ? null : account.getCardNo();
            boolean identityVerified = card != null && expectedCard != null
                    && card.equalsIgnoreCase(expectedCard);
            if (card != null && !identityVerified) {
                throw new BizException(RecognitionMessages.cardMismatch(card, expectedCard));
            }

            CalibrationDtos.RecordView record = createDraft(
                    video, best, identityVerified, user, ip);
            String identityMessage = identityVerified
                    ? "，人员卡核验通过"
                    : "，未检测到人员卡，需人工核验";
            String reviewMessage = Boolean.TRUE.equals(best.getNeedsReview())
                    ? "，识别置信度偏低，需重点复核"
                    : "";
            String message = "识别完成（" + best.getProfile() + "），已生成记录 "
                    + record.getRecordNo() + identityMessage + reviewMessage;
            videoMapper.completeAnalysis(videoId, limit(message, 500), card,
                    identityVerified, best.getProfile(), best.getConfidence(),
                    best.getAnomalyFrames());
            auditService.log(user.getId(), "VIDEO_ANALYZE", "VIDEO",
                    String.valueOf(videoId),
                    best.getProfile() + " -> " + record.getRecordNo(), ip);
            if (deleteSourceAfterSuccess && cleanupSource(videoId, videoPath)) {
                videoMapper.updateStatus(videoId, "ANALYZED",
                        limit(message + "，源视频已按策略清理", 500));
            }
        } catch (RuntimeException ex) {
            log.error("视频 {} 识别失败", videoId, ex);
            String message = ex instanceof BizException && ex.getMessage() != null
                    && !ex.getMessage().isBlank()
                            ? limit(ex.getMessage(), 500)
                            : RecognitionMessages.UNEXPECTED_FAILURE;
            videoMapper.updateStatus(videoId, "FAILED", message);
            auditService.log(user.getId(), "VIDEO_ANALYZE_FAILED", "VIDEO",
                    String.valueOf(videoId), message, ip);
        }
    }

    private RecognitionResult recognizeBest(Path videoPath, String balanceCode,
                                            List<java.math.BigDecimal> nominals) {
        List<String> configured = recognitionProperties.getDeviceProfiles()
                .get(balanceCode.toUpperCase(java.util.Locale.ROOT));
        String generic = recognitionProperties.getGenericProfile();
        List<String> profiles = configured == null
                ? new ArrayList<>() : new ArrayList<>(configured);
        if (profiles == null || profiles.isEmpty()) {
            if (generic == null || generic.isBlank()) {
                throw new BizException("暂不支持天平型号 " + balanceCode);
            }
            profiles.add(generic);
        }

        List<RecognitionResult> candidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        // 同一视频的多个显示档案并行识别：F002 有 3 个档案，串行会线性拉长等待时间。
        List<Future<RecognitionResult>> futures = new ArrayList<>();
        for (String profile : profiles) {
            futures.add(profileExecutor.submit(
                    () -> recognitionProcessService.run(videoPath, profile, nominals)));
        }
        for (int i = 0; i < futures.size(); i++) {
            String profile = profiles.get(i);
            try {
                candidates.add(futures.get(i).get());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BizException("视频识别被中断");
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                String safeMessage = cause instanceof BizException && cause.getMessage() != null
                        && !cause.getMessage().isBlank()
                                ? cause.getMessage() : RecognitionMessages.UNEXPECTED_FAILURE;
                log.warn("识别档案 {} 执行失败：{}", profile, cause.toString());
                errors.add(safeMessage);
            }
        }
        RecognitionResult specificBest = best(candidates);
        if (generic != null && !generic.isBlank()
                && !profiles.contains(generic)
                && readingCount(specificBest) < 9) {
            try {
                RecognitionResult genericResult =
                        recognitionProcessService.run(videoPath, generic, nominals);
                candidates.add(genericResult);
            } catch (RuntimeException ex) {
                log.warn("通用识别档案执行失败：{}", ex.toString());
                errors.add(ex instanceof BizException && ex.getMessage() != null
                        && !ex.getMessage().isBlank()
                        ? ex.getMessage() : RecognitionMessages.UNEXPECTED_FAILURE);
            }
        }
        RecognitionResult best = best(candidates);
        if (best == null) {
            log.error("所有识别档案均失败：video={}，errors={}", videoPath, errors);
            throw new BizException(errors.isEmpty()
                    ? RecognitionMessages.ALL_PROFILES_FAILED : errors.get(0));
        }
        return best;
    }

    private RecognitionResult best(List<RecognitionResult> candidates) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt(this::readingCount).reversed()
                        .thenComparingInt(this::displayUnitPenalty)
                        .thenComparingDouble(this::fitPenalty)
                        .thenComparing(RecognitionResult::getProfile))
                .orElse(null);
    }

    private CalibrationDtos.RecordView createDraft(Video video,
                                                   RecognitionResult result,
                                                   boolean identityVerified,
                                                   SessionUser user,
                                                   String ip) {
        BalanceView balance = balanceService.get(video.getBalanceId());
        LocalDateTime sourceTime = video.getRecordedAt() != null
                ? video.getRecordedAt() : video.getUploadedAt();
        LocalDateTime calibrationTime = sourceTime == null
                ? LocalDateTime.now().withSecond(0).withNano(0)
                : sourceTime.withSecond(0).withNano(0);

        CalibrationDtos.SaveRequest request = new CalibrationDtos.SaveRequest();
        request.setBalanceId(video.getBalanceId());
        request.setVideoId(video.getId());
        request.setCalibrationTime(calibrationTime);
        StringBuilder remark = new StringBuilder(
                "视频自动识别生成，请人工确认清洁、水平调节与过程信息");
        if (!identityVerified) {
            remark.append("；未检测到人员卡，请人工核验身份");
        }
        if (Boolean.TRUE.equals(result.getNeedsReview())) {
            remark.append("；识别置信度偏低或异常帧较多，请重点复核");
        }
        request.setRemark(remark.toString());
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
            boolean displayMg = weighing.getDisplayUnit() != null
                    ? "mg".equalsIgnoreCase(weighing.getDisplayUnit())
                    : "f002b".equals(result.getProfile()) && range.getSeqNo() > 1;
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
            input.setConfidence(weighing.getConfidence());
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

    private int displayUnitPenalty(RecognitionResult result) {
        if (result.getWeighings() == null) {
            return 1;
        }
        return result.getWeighings().stream()
                .anyMatch(row -> "mg".equalsIgnoreCase(row.getDisplayUnit()))
                ? 0 : 1;
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

    /** 识别成功后按策略删除源视频，释放磁盘；失败时保留文件便于重试。 */
    private boolean cleanupSource(Long videoId, Path videoPath) {
        try {
            videoMapper.clearSource(videoId);
        } catch (RuntimeException ex) {
            log.warn("源视频数据库状态清理失败，保留文件：video={}", videoId, ex);
            return false;
        }
        try {
            Files.deleteIfExists(videoPath);
            log.info("源视频已按策略清理：video={}", videoId);
        } catch (IOException ex) {
            // 数据库中的源路径已清空，用户不会再看到不可用视频；残留文件可由运维清理。
            log.warn("源视频状态已清理，但物理文件删除失败：video={}, path={}",
                    videoId, videoPath, ex);
        }
        return true;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

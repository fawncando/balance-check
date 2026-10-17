package com.bzd.balance.video;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.balance.Balance;
import com.bzd.balance.balance.BalanceMapper;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.bzd.balance.system.StorageService;

@Service
public class VideoService {

    private static final Set<String> ALLOWED_EXT = Set.of("mp4", "mov", "m4v", "avi", "mkv");

    private final VideoMapper videoMapper;
    private final BalanceMapper balanceMapper;
    private final AuditService auditService;
    private final Path uploadRoot;

    public VideoService(VideoMapper videoMapper,
                        BalanceMapper balanceMapper,
                        AuditService auditService,
                        StorageService storageService) {
        this.videoMapper = videoMapper;
        this.balanceMapper = balanceMapper;
        this.auditService = auditService;
        this.uploadRoot = storageService.getRoot();
    }

    public VideoView upload(MultipartFile file, Long balanceId, SessionUser user, String ip) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的视频文件");
        }
        String originalName = file.getOriginalFilename() == null ? "video" : file.getOriginalFilename();
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 mp4、mov、m4v、avi、mkv 格式");
        }
        if (balanceId != null) {
            Balance balance = balanceMapper.findById(balanceId);
            if (balance == null) {
                throw new BizException("所选天平不存在");
            }
        }

        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relativePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM")) + "/" + storedName;
        Path target = uploadRoot.resolve(relativePath).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BizException("非法的存储路径");
        }

        String sha256;
        long size;
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = file.getInputStream();
                 OutputStream rawOut = Files.newOutputStream(target);
                 DigestOutputStream out = new DigestOutputStream(rawOut, digest)) {
                size = in.transferTo(out);
                out.flush();
                sha256 = HexFormat.of().formatHex(digest.digest());
            }
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BizException(500, "保存视频失败：" + ex.getMessage());
        }

        Video video = new Video();
        video.setOriginalName(originalName);
        video.setStoredName(storedName);
        video.setRelativePath(relativePath);
        video.setSizeBytes(size);
        video.setSha256(sha256);
        video.setContentType(file.getContentType());
        video.setUploaderId(user.getId());
        video.setBalanceId(balanceId);
        video.setStatus("UPLOADED");
        video.setMessage(null);
        videoMapper.insert(video);

        auditService.log(user.getId(), "VIDEO_UPLOAD", "VIDEO", String.valueOf(video.getId()),
                originalName + " (" + size + " bytes)", ip);

        return videoMapper.findViewById(video.getId());
    }

    public List<VideoView> list(SessionUser user) {
        if (user.isAdmin() || user.isReviewer()) {
            return videoMapper.findAll();
        }
        return videoMapper.findByUploader(user.getId());
    }

    public void delete(Long id, SessionUser user, String ip) {
        Video video = videoMapper.findById(id);
        if (video == null) {
            throw new BizException(404, "视频不存在");
        }
        if (!video.getUploaderId().equals(user.getId()) && !user.isAdmin()) {
            throw new BizException(403, "只能删除自己上传的视频");
        }
        if (!"UPLOADED".equals(video.getStatus())) {
            throw new BizException("视频已进入识别流程，不能删除");
        }
        Path target = uploadRoot.resolve(video.getRelativePath()).normalize();
        if (target.startsWith(uploadRoot)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // 文件删除失败不影响记录清理，后续由运维处理
            }
        }
        videoMapper.deleteById(id);
        auditService.log(user.getId(), "VIDEO_DELETE", "VIDEO", String.valueOf(id),
                video.getOriginalName(), ip);
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

}

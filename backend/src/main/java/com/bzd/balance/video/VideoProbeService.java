package com.bzd.balance.video;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class VideoProbeService {

    private final ObjectMapper objectMapper;
    private final String configuredFfprobe;

    public VideoProbeService(
            ObjectMapper objectMapper,
            @Value("${app.recognition.ffprobe-command:ffprobe}")
            String configuredFfprobe) {
        this.objectMapper = objectMapper;
        this.configuredFfprobe = configuredFfprobe;
    }

    /**
     * Reject renamed non-video files before they receive a database record.
     */
    public VideoMetadata validate(Path video) {
        Process process;
        try {
            process = new ProcessBuilder(
                    resolveExecutable(configuredFfprobe),
                    "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries",
                    "stream=codec_type,width,height,duration:format=format_name,duration",
                    "-of", "json",
                    video.toString())
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            throw new BizException(500, "视频内容校验工具不可用");
        }

        try {
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BizException("视频内容校验超时");
            }
            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new BizException("文件内容不是有效的视频");
            }
            JsonNode root = objectMapper.readTree(output);
            JsonNode streams = root.path("streams");
            if (!streams.isArray() || streams.isEmpty()) {
                throw new BizException("文件不包含视频流");
            }
            JsonNode stream = streams.get(0);
            int width = stream.path("width").asInt(0);
            int height = stream.path("height").asInt(0);
            double duration = positiveDuration(stream.path("duration").asText(null));
            if (duration <= 0) {
                duration = positiveDuration(root.path("format").path("duration").asText(null));
            }
            if (!"video".equals(stream.path("codec_type").asText())
                    || width <= 0 || height <= 0 || duration <= 0) {
                throw new BizException("文件内容不是有效的视频");
            }
            return new VideoMetadata(
                    root.path("format").path("format_name").asText("unknown"),
                    duration, width, height);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "视频内容校验被中断");
        } catch (IOException ex) {
            throw new BizException("视频内容校验失败");
        } finally {
            process.destroy();
        }
    }

    private double positiveDuration(String value) {
        if (value == null || value.isBlank() || "N/A".equalsIgnoreCase(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String resolveExecutable(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute() && Files.isRegularFile(path)) {
            return path.normalize().toString();
        }
        if (!path.isAbsolute()) {
            Path relative = Path.of(System.getProperty("user.dir"))
                    .resolve(path)
                    .toAbsolutePath()
                    .normalize();
            if (Files.isRegularFile(relative)) {
                return relative.toString();
            }
        }
        return configured;
    }

    public record VideoMetadata(String formatName, double durationSeconds,
                                int width, int height) {
    }
}

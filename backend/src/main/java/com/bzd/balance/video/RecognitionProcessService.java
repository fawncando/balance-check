package com.bzd.balance.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RecognitionProcessService {

    private final ObjectMapper objectMapper;
    private final String configuredPython;
    private final String configuredScript;
    private final String configuredFfmpeg;
    private final long timeoutSeconds;

    public RecognitionProcessService(
            ObjectMapper objectMapper,
            @Value("${app.recognition.python-command:../analysis/.venv/Scripts/python.exe}")
            String configuredPython,
            @Value("${app.recognition.script-path:../analysis/analyze_video.py}")
            String configuredScript,
            @Value("${app.recognition.ffmpeg-command:ffmpeg}")
            String configuredFfmpeg,
            @Value("${app.recognition.timeout-seconds:600}")
            long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.configuredPython = configuredPython;
        this.configuredScript = configuredScript;
        this.configuredFfmpeg = configuredFfmpeg;
        this.timeoutSeconds = timeoutSeconds;
    }

    public RecognitionResult run(Path videoPath, String profile) {
        Path resultFile = null;
        Path logFile = null;
        try {
            resultFile = Files.createTempFile("balance-recognition-", ".json");
            logFile = Files.createTempFile("balance-recognition-", ".log");
            List<String> command = new ArrayList<>();
            command.add(resolveExecutable(configuredPython));
            command.add(resolveScript().toString());
            command.add("--video");
            command.add(videoPath.toString());
            command.add("--profile");
            command.add(profile);
            command.add("--ffmpeg");
            command.add(resolveExecutable(configuredFfmpeg));
            command.add("--out");
            command.add(resultFile.toString());

            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            Process process = builder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BizException(500, "视频识别超时");
            }
            if (process.exitValue() != 0) {
                throw new BizException(500,
                        "视频识别进程失败：" + tail(logFile, 1200));
            }
            return objectMapper.readValue(resultFile.toFile(), RecognitionResult.class);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "视频识别被中断");
        } catch (IOException ex) {
            throw new BizException(500, "启动视频识别失败：" + ex.getMessage());
        } finally {
            deleteQuietly(resultFile);
            deleteQuietly(logFile);
        }
    }

    private Path resolveScript() {
        Path configured = Path.of(configuredScript);
        if (configured.isAbsolute() && Files.isRegularFile(configured)) {
            return configured.normalize();
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        List<Path> candidates = List.of(
                workingDirectory.resolve(configured),
                workingDirectory.resolve("../analysis/analyze_video.py"),
                workingDirectory.resolve("analysis/analyze_video.py"));
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new BizException(500,
                        "找不到识别脚本：" + configuredScript));
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

    private String tail(Path file, int maxChars) {
        try {
            String text = Files.readString(file).strip();
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(text.length() - maxChars);
        } catch (IOException ex) {
            return ex.getMessage();
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Temporary files are best-effort cleanup.
        }
    }
}

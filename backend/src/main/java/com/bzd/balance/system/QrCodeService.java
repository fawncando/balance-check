package com.bzd.balance.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;

@Service
public class QrCodeService {

    private static final int CACHE_LIMIT = 128;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final String configuredPython;
    private final String configuredScript;
    private final Map<String, CachedQr> cache = new ConcurrentHashMap<>();

    private record CachedQr(byte[] png, Instant createdAt) {
    }

    public QrCodeService(
            @Value("${app.recognition.python-command:python}") String configuredPython,
            @Value("${app.recognition.qr-script:../analysis/generate_qr.py}")
            String configuredScript) {
        this.configuredPython = configuredPython;
        this.configuredScript = configuredScript;
    }

    /**
     * 生成二维码 PNG。同一文本在 10 分钟内直接复用结果，避免每次请求都启动 Python 进程。
     */
    public byte[] png(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException("二维码内容不能为空");
        }
        Instant now = Instant.now();
        CachedQr cached = cache.get(text);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(now)) {
            return cached.png();
        }
        byte[] png = generate(text);
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(text, new CachedQr(png, now));
        return png;
    }

    private byte[] generate(String text) {
        Path output = null;
        try {
            output = Files.createTempFile("balance-qr-", ".png");
            Process process = new ProcessBuilder(
                    resolveExecutable(configuredPython),
                    resolveScript().toString(),
                    "--text", text,
                    "--out", output.toString(),
                    "--size", "500")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BizException(500, "生成二维码超时");
            }
            if (process.exitValue() != 0) {
                throw new BizException(500, "生成二维码失败");
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "生成二维码被中断");
        } catch (IOException ex) {
            throw new BizException(500, "生成二维码失败：" + ex.getMessage());
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // Best-effort temporary file cleanup.
                }
            }
        }
    }

    private Path resolveScript() {
        Path configured = Path.of(configuredScript);
        if (configured.isAbsolute() && Files.isRegularFile(configured)) {
            return configured.normalize();
        }
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        List<Path> candidates = List.of(
                working.resolve(configured),
                working.resolve("../analysis/generate_qr.py"),
                working.resolve("analysis/generate_qr.py"));
        return candidates.stream().map(Path::normalize).filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new BizException(500, "找不到二维码生成脚本"));
    }

    private String resolveExecutable(String configured) {
        Path path = Path.of(configured);
        if (path.isAbsolute() && Files.isRegularFile(path)) {
            return path.toString();
        }
        Path relative = Path.of(System.getProperty("user.dir"))
                .resolve(path).toAbsolutePath().normalize();
        return Files.isRegularFile(relative) ? relative.toString() : configured;
    }
}

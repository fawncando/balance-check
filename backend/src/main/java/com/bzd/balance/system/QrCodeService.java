package com.bzd.balance.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;

@Service
public class QrCodeService {

    private final String configuredPython;
    private final String configuredScript;

    public QrCodeService(
            @Value("${app.recognition.python-command:python}") String configuredPython,
            @Value("${app.recognition.qr-script:../analysis/generate_qr.py}")
            String configuredScript) {
        this.configuredPython = configuredPython;
        this.configuredScript = configuredScript;
    }

    public byte[] png(String text) {
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

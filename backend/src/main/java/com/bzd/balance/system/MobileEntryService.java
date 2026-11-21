package com.bzd.balance.system;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;

@Service
public class MobileEntryService {

    private final String configuredPublicUrl;
    private final int mobilePort;
    private final String configuredPython;
    private final String configuredScript;

    public MobileEntryService(
            @Value("${app.public-url:}") String configuredPublicUrl,
            @Value("${app.mobile-port:5173}") int mobilePort,
            @Value("${app.recognition.python-command:python}") String configuredPython,
            @Value("${app.recognition.qr-script:../analysis/generate_qr.py}")
            String configuredScript) {
        this.configuredPublicUrl = configuredPublicUrl;
        this.mobilePort = mobilePort;
        this.configuredPython = configuredPython;
        this.configuredScript = configuredScript;
    }

    public Map<String, Object> entry() {
        String url = resolvedPublicUrl();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("path", "/mobile");
        body.put("lanIp", lanAddress());
        return body;
    }

    public byte[] qrPng() {
        Path output = null;
        try {
            output = Files.createTempFile("balance-mobile-qr-", ".png");
            Process process = new ProcessBuilder(
                    resolveExecutable(configuredPython),
                    resolveScript().toString(),
                    "--text", resolvedPublicUrl(),
                    "--out", output.toString(),
                    "--size", "360")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BizException(500, "生成手机入口二维码超时");
            }
            if (process.exitValue() != 0) {
                throw new BizException(500, "生成手机入口二维码失败");
            }
            return Files.readAllBytes(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "生成手机入口二维码被中断");
        } catch (IOException ex) {
            throw new BizException(500, "生成手机入口二维码失败：" + ex.getMessage());
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

    private String resolvedPublicUrl() {
        if (configuredPublicUrl != null && !configuredPublicUrl.isBlank()) {
            return configuredPublicUrl.replaceAll("/+$", "") + "/mobile";
        }
        return "http://" + lanAddress() + ":" + mobilePort + "/mobile";
    }

    private String lanAddress() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> values = network.getInetAddresses();
                while (values.hasMoreElements()) {
                    InetAddress address = values.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (IOException ex) {
            throw new BizException(500, "读取本机局域网地址失败：" + ex.getMessage());
        }
        return addresses.stream().sorted(Comparator.naturalOrder())
                .findFirst().orElse("127.0.0.1");
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
                .orElseThrow(() -> new BizException(500, "找不到手机入口二维码脚本"));
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

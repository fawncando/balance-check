package com.bzd.balance.system;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 选择手机上传入口应使用的局域网地址。
 *
 * 优先使用默认路由对应的本机地址，避免选中 WSL、Hyper-V 等虚拟网卡：
 * 这类网卡虽然也是 site-local 地址，但手机无法路由过去，二维码会失效。
 * 没有默认路由时，按网段与网卡名称打分挑选，排除链路本地地址与虚拟网卡。
 */
public final class LanAddressResolver {

    private static final List<String> VIRTUAL_HINTS = List.of(
            "vethernet", "wsl", "hyper-v", "virtualbox", "vmware", "vmnet", "tap",
            "tailscale", "zerotier", "docker", "virtual", "loopback", "bluetooth", "蓝牙");

    private static final String LOOPBACK = "127.0.0.1";

    private LanAddressResolver() {
    }

    /** 网卡候选：名称与 IPv4 地址。 */
    public static final class Candidate {
        private final String interfaceName;
        private final String address;

        public Candidate(String interfaceName, String address) {
            this.interfaceName = interfaceName;
            this.address = address;
        }

        public String getInterfaceName() { return interfaceName; }
        public String getAddress() { return address; }
    }

    public static String resolve() {
        String routed = defaultRouteAddress();
        if (routed != null) {
            return routed;
        }
        return pick(collectCandidates());
    }

    /** 通过默认路由探测本机出口地址；UDP connect 不会真正发包。 */
    static String defaultRouteAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address
                    && !local.isLoopbackAddress()
                    && !local.isLinkLocalAddress()
                    && local.isSiteLocalAddress()) {
                String ip = local.getHostAddress();
                String name = interfaceNameOf(ip);
                if (name == null || !isVirtualName(name)) {
                    return ip;
                }
            }
        } catch (Exception ignored) {
            // 无默认路由或网络不可用时退回网卡枚举
        }
        return null;
    }

    static String interfaceNameOf(String ip) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.getHostAddress().equals(ip)) {
                        return network.getName() + " " + network.getDisplayName();
                    }
                }
            }
        } catch (Exception ignored) {
            // 读取网卡失败时按未知处理
        }
        return null;
    }

    /** 从候选中选出最合适的地址；全部不合格时回退 127.0.0.1。 */
    public static String pick(List<Candidate> candidates) {
        if (candidates == null) {
            return LOOPBACK;
        }
        String best = LOOPBACK;
        int bestScore = -1;
        for (Candidate candidate : candidates) {
            int value = score(candidate.getAddress(), candidate.getInterfaceName());
            if (value < 0) {
                continue;
            }
            if (value > bestScore || (value == bestScore && candidate.getAddress().compareTo(best) < 0)) {
                best = candidate.getAddress();
                bestScore = value;
            }
        }
        return bestScore < 0 ? LOOPBACK : best;
    }

    /**
     * 网卡打分：只接受私有网段，虚拟网卡与链路本地地址记为 -1。
     * 192.168 优先于 10 网段，10 网段优先于 172.16-31，物理网卡名称再加分。
     */
    public static int score(String ip, String interfaceName) {
        if (ip == null || !ip.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return -1;
        }
        if (ip.startsWith("127.") || ip.startsWith("169.254.")) {
            return -1;
        }
        String name = interfaceName == null ? "" : interfaceName.toLowerCase(Locale.ROOT);
        if (isVirtualName(name)) {
            return -1;
        }
        int value;
        if (ip.startsWith("192.168.")) {
            value = 100;
        } else if (ip.startsWith("10.")) {
            value = 80;
        } else if (isPrivate172(ip)) {
            value = 60;
        } else {
            return -1;
        }
        if (name.contains("wlan") || name.contains("wi-fi") || name.contains("wifi")
                || name.contains("无线") || name.contains("以太") || name.contains("ethernet")) {
            value += 10;
        }
        return value;
    }

    public static boolean isVirtualName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String hint : VIRTUAL_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPrivate172(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return "172".equals(parts[0]) && second >= 16 && second <= 31;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    static List<Candidate> collectCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                String name = network.getName() + " " + network.getDisplayName();
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()
                            && !address.isLinkLocalAddress()) {
                        candidates.add(new Candidate(name, address.getHostAddress()));
                    }
                }
            }
        } catch (Exception ignored) {
            // 枚举失败时由调用方回退
        }
        return candidates;
    }
}

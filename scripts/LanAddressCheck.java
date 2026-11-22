import java.util.List;

import com.bzd.balance.system.LanAddressResolver;

/**
 * 局域网地址选择规则的独立校验：虚拟网卡过滤、网段优先级与回退。
 * 由 scripts\batch7-unit-check.ps1 编译并执行。
 */
public class LanAddressCheck {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        check("虚拟网卡名称被识别",
                LanAddressResolver.isVirtualName("vEthernet (WSL (Hyper-V firewall))")
                        && LanAddressResolver.isVirtualName("VMware Network Adapter VMnet8")
                        && LanAddressResolver.isVirtualName("VirtualBox Host-Only Network")
                        && !LanAddressResolver.isVirtualName("WLAN")
                        && !LanAddressResolver.isVirtualName("以太网"));

        check("虚拟网卡的地址被排除",
                LanAddressResolver.score("172.30.144.1", "vEthernet (WSL)") < 0
                        && LanAddressResolver.score("192.168.56.1", "VirtualBox Host-Only Network") < 0);

        check("物理网卡优先于 WSL 虚拟网卡",
                "192.168.0.111".equals(LanAddressResolver.pick(List.of(
                        new LanAddressResolver.Candidate("vEthernet (WSL (Hyper-V firewall))", "172.30.144.1"),
                        new LanAddressResolver.Candidate("wlan0 WLAN", "192.168.0.111")))));

        check("192.168 网段优先于 10 与 172 网段",
                LanAddressResolver.score("192.168.1.5", "以太网")
                        > LanAddressResolver.score("10.0.0.5", "以太网")
                        && LanAddressResolver.score("10.0.0.5", "以太网")
                        > LanAddressResolver.score("172.20.0.5", "以太网"));

        check("链路本地与非法地址被排除并回退 127.0.0.1",
                LanAddressResolver.score("169.254.10.20", "以太网") < 0
                        && "127.0.0.1".equals(LanAddressResolver.pick(List.of(
                                new LanAddressResolver.Candidate("以太网", "169.254.10.20"))))
                        && "127.0.0.1".equals(LanAddressResolver.pick(List.of())));

        System.out.println();
        System.out.println("结果：" + pass + " 通过，" + fail + " 失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("PASS  " + name);
        } else {
            fail++;
            System.out.println("FAIL  " + name);
        }
    }
}

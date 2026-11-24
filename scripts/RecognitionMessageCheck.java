import java.math.BigDecimal;

import com.bzd.balance.video.RecognitionMessages;
import com.bzd.balance.video.RecognitionResult;

/**
 * 识别失败提示的独立校验：文案要具体、可读，且不得泄露堆栈或服务器路径。
 * 由 scripts\batch6-unit-check.ps1 编译并执行。
 */
public class RecognitionMessageCheck {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        check("次数不足时提示未识别完整",
                RecognitionMessages.incompleteWeighings(7, 9).contains("未识别出完整的九次称量"));

        check("次数不足时给出具体次数",
                RecognitionMessages.incompleteWeighings(7, 9).contains("7/9"));

        check("没有识别到数据时提示明确",
                RecognitionMessages.incompleteWeighings(0, 9).contains("未识别出称量数据"));

        check("账号未绑卡时不出现 null 字面量",
                RecognitionMessages.cardMismatch("RY-001", null).contains("未绑定人员卡")
                        && !RecognitionMessages.cardMismatch("RY-001", null).contains("null"));

        check("卡号不一致时提示两张卡号",
                RecognitionMessages.cardMismatch("RY-001", "RY-002").contains("RY-001")
                        && RecognitionMessages.cardMismatch("RY-001", "RY-002").contains("RY-002"));

        check("失败文案不含堆栈或绝对路径",
                safe(RecognitionMessages.PROCESS_FAILURE)
                        && safe(RecognitionMessages.START_FAILURE)
                        && safe(RecognitionMessages.NOT_READY)
                        && safe(RecognitionMessages.ALL_PROFILES_FAILED)
                        && safe(RecognitionMessages.UNEXPECTED_FAILURE));

        RecognitionResult low = new RecognitionResult();
        low.setConfidence(new BigDecimal("8.99"));
        check("低置信度识别按失败处理",
                low.isConfidenceBelow(new BigDecimal("30")));

        RecognitionResult genuineFailure = new RecognitionResult();
        genuineFailure.setConfidence(new BigDecimal("84.62"));
        check("真实超差不会被安全阈值拦截",
                !genuineFailure.isConfidenceBelow(new BigDecimal("30")));

        String lowMessage = RecognitionMessages.lowConfidence(
                new BigDecimal("8.99"), new BigDecimal("30"));
        check("低置信度提示解释天平可能选错",
                safe(lowMessage)
                        && lowMessage.contains("天平")
                        && lowMessage.contains("设备一致")
                        && lowMessage.contains("停止生成记录"));

        check("低置信度提示明确真实超差仍会建单",
                lowMessage.contains("真实超差")
                        && lowMessage.contains("超差"));

        System.out.println();
        System.out.println("结果：" + pass + " 通过，" + fail + " 失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static boolean safe(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains("traceback")
                && !lower.contains(".py")
                && !lower.contains("line ")
                && !lower.contains("c:\\")
                && !lower.contains("/users/")
                && !lower.contains("onedrive");
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

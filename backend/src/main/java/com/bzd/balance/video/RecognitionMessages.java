package com.bzd.balance.video;

/**
 * 视频识别失败时返回给用户的安全提示。
 *
 * 这些文案会写入 video.message 并直接展示在前端，因此不得包含 Python 堆栈、
 * 子进程日志或服务器绝对路径；详细原因由服务端日志记录。
 */
public final class RecognitionMessages {

    /** 识别子进程非 0 退出。 */
    public static final String PROCESS_FAILURE = "视频识别失败，请检查视频格式后重试，或联系管理员";

    /** 识别子进程无法启动（Python 路径、脚本或依赖异常）。 */
    public static final String START_FAILURE = "视频识别服务启动失败，请联系管理员检查识别环境";

    /** 找不到识别脚本或脚本未就绪。 */
    public static final String NOT_READY = "视频识别服务未就绪，请联系管理员";

    /** 所有识别档案都没有返回可用结果。 */
    public static final String ALL_PROFILES_FAILED = "视频识别失败，请检查视频格式与天平型号后重试";

    /** 非业务异常导致的失败。 */
    public static final String UNEXPECTED_FAILURE = "视频识别失败，请查看服务端日志或联系管理员";

    private RecognitionMessages() {
    }

    /** 称量次数不足时给出具体次数，便于用户判断是拍摄问题还是天平选错。 */
    public static String incompleteWeighings(int found, int expected) {
        if (found <= 0) {
            return "未识别出称量数据，请检查视频与天平选择";
        }
        return "未识别出完整的九次称量（仅识别到 " + found + "/" + expected
                + " 次），请检查视频与天平选择";
    }

    /** 人员卡不一致或账号未绑卡时的提示，避免出现字面量 null。 */
    public static String cardMismatch(String detectedCard, String expectedCard) {
        if (expectedCard == null || expectedCard.isBlank()) {
            return "当前账号未绑定人员卡（视频检测到 " + detectedCard
                    + "），请先在人员管理中绑定后再上传";
        }
        return "人员卡号 " + detectedCard + " 与当前登录账号 " + expectedCard + " 不一致";
    }
}

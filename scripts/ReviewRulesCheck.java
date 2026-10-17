import com.bzd.balance.calibration.ReviewRules;
import com.bzd.balance.common.BizException;

/**
 * 复核规则的独立校验程序。
 *
 * 项目离线仓库里没有 JUnit 依赖，复核规则又只涉及纯判定，因此用这个 runner 单独验证；
 * 由 scripts\batch6-unit-check.ps1 编译并执行，绕过 Maven 的 testCompile。
 */
public class ReviewRulesCheck {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        check("只有待复核的记录可以复核",
                ReviewRules.canReview("SUBMITTED")
                        && !ReviewRules.canReview("DRAFT")
                        && !ReviewRules.canReview("REVIEWED")
                        && !ReviewRules.canReview(null));
        check("只有已复核的记录可以解锁",
                ReviewRules.canReopen("REVIEWED")
                        && !ReviewRules.canReopen("SUBMITTED")
                        && !ReviewRules.canReopen("DRAFT"));
        check("复核结论大小写与空白不敏感",
                ReviewRules.APPROVED.equals(ReviewRules.normalizeResult(" approved "))
                        && ReviewRules.REJECTED.equals(ReviewRules.normalizeResult("Rejected")));
        check("非法复核结论按参数错误拒绝",
                codeOf(() -> ReviewRules.normalizeResult("PASS")) == 400
                        && codeOf(() -> ReviewRules.normalizeResult("   ")) == 400
                        && codeOf(() -> ReviewRules.normalizeResult(null)) == 400);
        check("退回必须填写意见，通过可不填",
                ReviewRules.commentRequired(ReviewRules.REJECTED)
                        && !ReviewRules.commentRequired(ReviewRules.APPROVED));
        check("通过即锁定，退回回到草稿",
                "REVIEWED".equals(ReviewRules.statusAfterReview(ReviewRules.APPROVED))
                        && "DRAFT".equals(ReviewRules.statusAfterReview(ReviewRules.REJECTED)));
        check("记录状态只认三种取值",
                ReviewRules.isKnownStatus("DRAFT")
                        && ReviewRules.isKnownStatus("SUBMITTED")
                        && ReviewRules.isKnownStatus("REVIEWED")
                        && !ReviewRules.isKnownStatus("APPROVED")
                        && !ReviewRules.isKnownStatus(""));

        System.out.println();
        System.out.println("结果：" + pass + " 通过，" + fail + " 失败");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static int codeOf(Runnable action) {
        try {
            action.run();
            return 0;
        } catch (BizException ex) {
            return ex.getCode();
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

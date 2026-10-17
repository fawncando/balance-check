package com.bzd.balance.auth;

import com.bzd.balance.common.BizException;

import jakarta.servlet.http.HttpSession;

public final class CurrentUser {

    public static final String SESSION_KEY = "BALANCE_CHECK_USER";

    private CurrentUser() {
    }

    public static SessionUser require(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof SessionUser user) {
            return user;
        }
        throw new BizException(401, "未登录或会话已过期");
    }
}

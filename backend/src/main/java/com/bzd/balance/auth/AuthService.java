package com.bzd.balance.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.bzd.balance.user.User;
import com.bzd.balance.user.UserMapper;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, AuditService auditService,
                       LoginAttemptService loginAttemptService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
    }

    public SessionUser login(String username, String password, String ip) {
        loginAttemptService.assertAllowed(username, ip);
        User user = userMapper.findByUsername(username);
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            loginAttemptService.recordFailure(username, ip);
            throw new BizException(401, "账号或密码错误");
        }
        loginAttemptService.recordSuccess(username, ip);
        SessionUser sessionUser = new SessionUser(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.getCardNo(), user.getSessionVersion());
        auditService.log(user.getId(), "LOGIN", "USER", String.valueOf(user.getId()), "登录成功", ip);
        return sessionUser;
    }
}

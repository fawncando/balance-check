package com.bzd.balance.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;

@Service
public class UserAdminService {

    private static final Set<String> ROLES = Set.of("INSPECTOR", "REVIEWER", "ADMIN");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    private final UserMapper userMapper;
    private final AuditService auditService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserAdminService(UserMapper userMapper, AuditService auditService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    @Transactional
    public UserView create(UserCreateRequest request, SessionUser actor, String ip) {
        requireAdmin(actor);
        String username = normalizeUsername(request == null ? null : request.getUsername());
        String displayName = normalizeDisplayName(request == null ? null : request.getDisplayName());
        String password = normalizePassword(request == null ? null : request.getPassword());
        String role = normalizeRole(request == null ? null : request.getRole());
        if (userMapper.findIdByUsername(username) != null) {
            throw new BizException("登录账号已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setStatus("ACTIVE");
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BizException("登录账号已存在");
        }
        auditService.log(actor.getId(), "USER_CREATE", "USER", String.valueOf(user.getId()),
                username + "：" + displayName + " / " + role, ip);
        return toView(requireUser(user.getId()));
    }

    @Transactional
    public UserView update(Long id,
                           UserUpdateRequest request,
                           SessionUser actor,
                           String ip) {
        requireAdmin(actor);
        User target = requireUser(id);
        String displayName = normalizeDisplayName(request == null ? null : request.getDisplayName());
        String role = normalizeRole(request == null ? null : request.getRole());
        String status = normalizeStatus(request == null ? null : request.getStatus());

        if (target.getId().equals(actor.getId())
                && (!role.equals(target.getRole()) || !"ACTIVE".equals(status))) {
            throw new BizException("不能修改自己的角色或停用自己的账号");
        }
        boolean removesAdmin = "ADMIN".equals(target.getRole())
                && (!"ADMIN".equals(role) || !"ACTIVE".equals(status));
        if (removesAdmin && userMapper.countOtherActiveAdmins(id) == 0) {
            throw new BizException("系统必须保留至少一个启用的管理员");
        }

        String before = target.getDisplayName() + " / " + target.getRole() + " / " + target.getStatus();
        target.setDisplayName(displayName);
        target.setRole(role);
        target.setStatus(status);
        userMapper.updateProfile(target);
        auditService.log(actor.getId(), "USER_UPDATE", "USER", String.valueOf(id),
                before + " -> " + displayName + " / " + role + " / " + status, ip);
        return toView(requireUser(id));
    }

    @Transactional
    public void resetPassword(Long id,
                              PasswordResetRequest request,
                              SessionUser actor,
                              String ip) {
        requireAdmin(actor);
        User target = requireUser(id);
        String password = normalizePassword(request == null ? null : request.getPassword());
        userMapper.updatePassword(id, encoder.encode(password));
        auditService.log(actor.getId(), "USER_PASSWORD_RESET", "USER", String.valueOf(id),
                target.getUsername(), ip);
    }

    private User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private void requireAdmin(SessionUser user) {
        if (!user.isAdmin()) {
            throw new BizException(403, "只有管理员可以维护人员与权限");
        }
    }

    private String normalizeUsername(String value) {
        String username = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_.-]{3,30}")) {
            throw new BizException("登录账号只能包含 3 至 30 位字母、数字、点、下划线或短横线");
        }
        return username;
    }

    private String normalizeDisplayName(String value) {
        String displayName = value == null ? "" : value.trim();
        if (displayName.isEmpty() || displayName.length() > 50) {
            throw new BizException("姓名不能为空且不能超过 50 个字符");
        }
        return displayName;
    }

    private String normalizePassword(String value) {
        String password = value == null ? "" : value;
        if (password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException("密码至少 6 个字符，且不能超过 72 个 UTF-8 字节");
        }
        return password;
    }

    private String normalizeRole(String value) {
        String role = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw new BizException("角色取值不合法");
        }
        return role;
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BizException("账号状态取值不合法");
        }
        return status;
    }

    private UserView toView(User user) {
        UserView view = new UserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setDisplayName(user.getDisplayName());
        view.setRole(user.getRole());
        view.setCardNo(user.getCardNo());
        view.setStatus(user.getStatus());
        return view;
    }
}

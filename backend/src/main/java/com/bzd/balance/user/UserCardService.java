package com.bzd.balance.user;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.BizException;
import com.bzd.balance.log.AuditService;
import com.bzd.balance.system.QrCodeService;

@Service
public class UserCardService {

    private final UserMapper userMapper;
    private final QrCodeService qrCodeService;
    private final AuditService auditService;

    public UserCardService(UserMapper userMapper,
                           QrCodeService qrCodeService,
                           AuditService auditService) {
        this.userMapper = userMapper;
        this.qrCodeService = qrCodeService;
        this.auditService = auditService;
    }

    public List<UserView> list(SessionUser user) {
        requireAdmin(user);
        return userMapper.findAll().stream().map(this::toView).toList();
    }

    public UserView updateCard(Long id,
                               UserCardRequest request,
                               SessionUser actor,
                               String ip) {
        requireAdmin(actor);
        User target = requireUser(id);
        String cardNo = normalize(request == null ? null : request.getCardNo());
        Long owner = userMapper.findOtherUserIdByCard(cardNo, id);
        if (owner != null) {
            throw new BizException("该人员卡号已绑定其他账号");
        }
        String previous = target.getCardNo();
        try {
            userMapper.updateCardNo(id, cardNo);
        } catch (DuplicateKeyException ex) {
            // 并发绑定同一卡号时由数据库唯一索引兜底，转成可读提示
            throw new BizException("该人员卡号已绑定其他账号");
        }
        auditService.log(actor.getId(), "USER_CARD_BIND", "USER", String.valueOf(id),
                target.getDisplayName() + "：" + (previous == null ? "未绑定" : previous)
                        + " -> " + cardNo, ip);
        return toView(requireUser(id));
    }

    public UserView clearCard(Long id, SessionUser actor, String ip) {
        requireAdmin(actor);
        User target = requireUser(id);
        if (target.getCardNo() == null || target.getCardNo().isBlank()) {
            throw new BizException("该账号尚未绑定人员卡");
        }
        String previous = target.getCardNo();
        userMapper.updateCardNo(id, null);
        auditService.log(actor.getId(), "USER_CARD_UNBIND", "USER", String.valueOf(id),
                target.getDisplayName() + "：解绑 " + previous, ip);
        return toView(requireUser(id));
    }

    public byte[] cardQr(Long id, SessionUser actor) {
        requireAdmin(actor);
        User target = requireUser(id);
        if (target.getCardNo() == null || target.getCardNo().isBlank()) {
            throw new BizException("该账号尚未绑定人员卡");
        }
        return qrCodeService.png(target.getCardNo());
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
            throw new BizException(403, "只有管理员可以维护人员卡");
        }
    }

    private String normalize(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            throw new BizException("请填写人员卡号");
        }
        String value = cardNo.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9_-]{2,20}")) {
            throw new BizException("人员卡号只能包含 2 至 20 位字母、数字、下划线或短横线");
        }
        return value;
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

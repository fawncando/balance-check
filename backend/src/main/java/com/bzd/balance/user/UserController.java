package com.bzd.balance.user;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bzd.balance.auth.CurrentUser;
import com.bzd.balance.auth.SessionUser;
import com.bzd.balance.common.ApiResponse;
import com.bzd.balance.common.BizException;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 用户列表：操作日志按操作人筛选时使用，只对管理员开放。 */
    @GetMapping
    public ApiResponse<List<UserView>> list(HttpSession session) {
        SessionUser user = CurrentUser.require(session);
        if (!user.isAdmin()) {
            throw new BizException(403, "只有管理员可以查看用户列表");
        }
        List<UserView> views = new ArrayList<>();
        for (User row : userMapper.findAll()) {
            UserView view = new UserView();
            view.setId(row.getId());
            view.setUsername(row.getUsername());
            view.setDisplayName(row.getDisplayName());
            view.setRole(row.getRole());
            views.add(view);
        }
        return ApiResponse.ok(views);
    }
}

package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.User;
import com.asset.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 * 处理用户信息查询
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取所有用户列表
     * @return 用户列表（已脱敏）
     */
    @GetMapping("/list")
    public Result<List<User>> list() {
        List<User> users = userService.list();
        // 敏感信息不返回前端
        users.forEach(user -> user.setPasswordHash(null));
        return Result.success(users);
    }
}

package com.asset.controller;

import com.asset.common.Result;
import com.asset.common.TokenUtils;
import com.asset.entity.User;
import com.asset.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenUtils tokenUtils;

    @org.springframework.beans.factory.annotation.Value("${auth.verify-internal-password:false}")
    private boolean verifyInternalPassword;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        log.info("Attempting to log in user: {}", username);
        User user;
        try {
            log.info("Querying database for user: {}", username);
            user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            log.info("Database query finished for user: {}", username);
        } catch (Exception e) {
            log.error("Database query failed for user: {}", username, e);
            return Result.error("数据库查询异常，请检查后台日志");
        }

        if (user == null) {
            log.warn("Login failed for user '{}': user not found", username);
            return Result.error("用户不存在");
        }
        
        // 角色逻辑：
        // 1. role_type = 4 (外购人员) 永远校验密码
        // 2. role_type = 1, 2, 3 (内部人员) 根据配置 verifyInternalPassword 决定是否校验
        boolean needVerify = false;
        if (user.getRoleType() != null) {
            if (user.getRoleType() == 4) {
                needVerify = true;
            } else if (verifyInternalPassword) {
                needVerify = true;
            }
        }

        if (needVerify) {
            if (password == null || !password.equals(user.getPasswordHash())) {
                log.warn("Login failed for user '{}': incorrect password", username);
                return Result.error("密码错误");
            }
        }

        log.info("Login successful for user '{}'", username);
        Map<String, Object> data = new HashMap<>();
        data.put("token", tokenUtils.generateToken(user.getId()));
        data.put("user", user);

        return Result.success(data);
    }
}

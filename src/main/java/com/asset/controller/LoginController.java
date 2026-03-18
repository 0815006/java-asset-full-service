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

@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenUtils tokenUtils;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");

        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        // 角色逻辑：4=外购人员，需要校验密码
        if (user.getRoleType() != null && user.getRoleType() == 4) {
            if (password == null || !password.equals(user.getPasswordHash())) {
                return Result.error("密码错误");
            }
        }
        // 其他角色（1, 2, 3）直接登录，不校验密码

        Map<String, Object> data = new HashMap<>();
        data.put("token", tokenUtils.generateToken(user.getId()));
        data.put("user", user);

        return Result.success(data);
    }
}

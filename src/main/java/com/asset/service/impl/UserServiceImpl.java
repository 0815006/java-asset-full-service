package com.asset.service.impl;

import com.asset.entity.User;
import com.asset.mapper.UserMapper;
import com.asset.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public boolean changePassword(Integer userId, String oldPassword, String newPassword) {
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getId, userId));
        if (user == null) {
            return false; // 用户不存在
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return false; // 旧密码不正确
        }

        // 加密新密码并更新
        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newPasswordHash);
        return this.updateById(user);
    }
}

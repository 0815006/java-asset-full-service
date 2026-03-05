package com.asset.service.impl;

import com.asset.entity.User;
import com.asset.mapper.UserMapper;
import com.asset.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}

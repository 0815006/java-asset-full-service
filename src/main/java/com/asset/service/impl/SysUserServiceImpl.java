package com.asset.service.impl;

import com.asset.entity.SysUser;
import com.asset.mapper.SysUserMapper;
import com.asset.service.SysUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}

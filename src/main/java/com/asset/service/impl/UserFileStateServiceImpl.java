package com.asset.service.impl;

import com.asset.entity.UserFileState;
import com.asset.mapper.UserFileStateMapper;
import com.asset.service.UserFileStateService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserFileStateServiceImpl extends ServiceImpl<UserFileStateMapper, UserFileState> implements UserFileStateService {
}

package com.asset.service.impl;

import com.asset.entity.EditLock;
import com.asset.mapper.EditLockMapper;
import com.asset.service.EditLockService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EditLockServiceImpl extends ServiceImpl<EditLockMapper, EditLock> implements EditLockService {
}

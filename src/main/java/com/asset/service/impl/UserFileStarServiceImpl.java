package com.asset.service.impl;

import com.asset.entity.UserFileStar;
import com.asset.mapper.UserFileStarMapper;
import com.asset.service.UserFileStarService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class UserFileStarServiceImpl extends ServiceImpl<UserFileStarMapper, UserFileStar> implements UserFileStarService {

    @Override
    public List<Map<String, Object>> getGlobalStarTop(int limit) {
        // 直接调用 Mapper 中的自定义方法
        return baseMapper.selectGlobalStarTop(limit);
    }
}

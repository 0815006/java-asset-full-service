package com.asset.service.impl;

import com.asset.entity.AssetAccessLog;
import com.asset.mapper.AssetAccessLogMapper;
import com.asset.service.AssetAccessLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AssetAccessLogServiceImpl extends ServiceImpl<AssetAccessLogMapper, AssetAccessLog> implements AssetAccessLogService {

    @Override
    public List<Map<String, Object>> getGlobalUseTop(int limit) {
        return baseMapper.selectGlobalUseTop(limit);
    }

    @Override
    public List<Map<String, Object>> getProductUseTop(Long productId, int limit) {
        return baseMapper.selectProductUseTop(productId, limit);
    }
}

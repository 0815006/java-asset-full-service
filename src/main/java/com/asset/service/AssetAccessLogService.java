package com.asset.service;

import com.asset.entity.AssetAccessLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface AssetAccessLogService extends IService<AssetAccessLog> {
    List<Map<String, Object>> getGlobalUseTop(int limit);
    List<Map<String, Object>> getProductUseTop(Long productId, int limit);
}

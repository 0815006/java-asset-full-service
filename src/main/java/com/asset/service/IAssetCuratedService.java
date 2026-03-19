package com.asset.service;

import com.asset.entity.AssetCurated;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IAssetCuratedService extends IService<AssetCurated> {
    List<AssetCurated> getCuratedAssetsByProductId(Long productId);
    void toggleCuratedStatus(Long fileId, Long productId, boolean isCurated);
    boolean isCurated(Long fileId, Long productId);
}

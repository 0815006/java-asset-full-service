package com.asset.service.impl;

import com.asset.entity.AssetCurated;
import com.asset.mapper.AssetCuratedMapper;
import com.asset.service.IAssetCuratedService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AssetCuratedServiceImpl extends ServiceImpl<AssetCuratedMapper, AssetCurated> implements IAssetCuratedService {

    @Override
    public List<AssetCurated> getCuratedAssetsByProductId(Long productId) {
        LambdaQueryWrapper<AssetCurated> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetCurated::getProductId, productId);
        return list(queryWrapper);
    }

    @Override
    public void toggleCuratedStatus(Long fileId, Long productId, boolean isCurated) {
        LambdaQueryWrapper<AssetCurated> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetCurated::getFileId, fileId)
                .eq(AssetCurated::getProductId, productId);

        if (isCurated) {
            // Add if not exists
            if (count(queryWrapper) == 0) {
                AssetCurated curated = new AssetCurated();
                curated.setFileId(fileId);
                curated.setProductId(productId);
                curated.setCreatedAt(LocalDateTime.now());
                // You might want to set a default display order here
                curated.setDisplayOrder(0); 
                save(curated);
            }
        } else {
            // Remove if exists
            remove(queryWrapper);
        }
    }

    @Override
    public boolean isCurated(Long fileId, Long productId) {
        LambdaQueryWrapper<AssetCurated> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AssetCurated::getFileId, fileId)
                .eq(AssetCurated::getProductId, productId);
        return count(queryWrapper) > 0;
    }
}

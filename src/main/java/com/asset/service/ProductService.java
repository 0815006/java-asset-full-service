package com.asset.service;

import com.asset.entity.Product;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ProductService extends IService<Product> {
    /**
     * 更新产品的资产总数
     * @param productId 产品ID
     * @param delta 变化量 (1 或 -1)
     */
    void updateAssetCount(Long productId, int delta);
}

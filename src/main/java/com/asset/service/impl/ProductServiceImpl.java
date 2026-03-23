package com.asset.service.impl;

import com.asset.entity.Product;
import com.asset.mapper.ProductMapper;
import com.asset.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
    @Override
    public void updateAssetCount(Long productId, int delta) {
        if (productId == null || productId == 0) {
            return;
        }
        // 使用 MyBatis Plus 的 update 语句直接在数据库层面进行原子操作
        this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product>()
                .eq(Product::getId, productId)
                .setSql("asset_count = asset_count + " + delta));
    }
}

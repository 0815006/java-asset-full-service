package com.asset.service.impl;

import com.asset.dto.ProductUseRankingDTO;
import com.asset.entity.AssetAccessLog;
import com.asset.mapper.ProductUseRankingMapper;
import com.asset.service.IProductUseRankingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductUseRankingServiceImpl extends ServiceImpl<ProductUseRankingMapper, AssetAccessLog> implements IProductUseRankingService {

    @Autowired
    private ProductUseRankingMapper productUseRankingMapper;

    @Override
    public List<ProductUseRankingDTO> getProductUseRanking(Long productId) {
        return productUseRankingMapper.getProductUseRanking(productId);
    }
}

package com.asset.service;

import com.asset.dto.ProductUseRankingDTO;
import com.asset.entity.AssetAccessLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface IProductUseRankingService extends IService<AssetAccessLog> {
    List<ProductUseRankingDTO> getProductUseRanking(Long productId);
}

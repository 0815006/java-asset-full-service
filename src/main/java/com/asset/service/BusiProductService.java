package com.asset.service;

import com.asset.dto.BusiProductDTO;
import com.asset.dto.ProductQueryDTO;
import com.asset.entity.BusiProduct;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface BusiProductService extends IService<BusiProduct> {
    IPage<BusiProductDTO> getProductList(ProductQueryDTO query);
}

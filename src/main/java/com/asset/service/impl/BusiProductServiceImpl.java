package com.asset.service.impl;

import com.asset.dto.BusiProductDTO;
import com.asset.dto.ProductQueryDTO;
import com.asset.entity.BusiProduct;
import com.asset.mapper.BusiProductMapper;
import com.asset.service.BusiProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BusiProductServiceImpl extends ServiceImpl<BusiProductMapper, BusiProduct> implements BusiProductService {

    @Override
    public IPage<BusiProductDTO> getProductList(ProductQueryDTO query) {
        Page<BusiProductDTO> page = new Page<>(query.getPage(), query.getSize());
        return baseMapper.selectProductList(page, query);
    }
}

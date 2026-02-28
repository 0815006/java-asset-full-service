package com.asset.mapper;

import com.asset.dto.BusiProductDTO;
import com.asset.dto.ProductQueryDTO;
import com.asset.entity.BusiProduct;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusiProductMapper extends BaseMapper<BusiProduct> {
    IPage<BusiProductDTO> selectProductList(Page<BusiProductDTO> page, @Param("query") ProductQueryDTO query);
}

package com.asset.service.impl;

import com.asset.entity.BusiProduct;
import com.asset.mapper.BusiProductMapper;
import com.asset.service.BusiProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BusiProductServiceImpl extends ServiceImpl<BusiProductMapper, BusiProduct> implements BusiProductService {
}

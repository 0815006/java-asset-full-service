package com.asset.service.impl;

import com.asset.entity.Product;
import com.asset.mapper.ProductMapper;
import com.asset.service.ProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
}

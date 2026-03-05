package com.asset.service.impl;

import com.asset.entity.UserFavoriteProduct;
import com.asset.mapper.UserFavoriteProductMapper;
import com.asset.service.UserFavoriteProductService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class UserFavoriteProductServiceImpl extends ServiceImpl<UserFavoriteProductMapper, UserFavoriteProduct> implements UserFavoriteProductService {
}

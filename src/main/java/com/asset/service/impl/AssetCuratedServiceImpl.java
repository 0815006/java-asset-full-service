package com.asset.service.impl;

import com.asset.entity.AssetCurated;
import com.asset.mapper.AssetCuratedMapper;
import com.asset.service.AssetCuratedService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AssetCuratedServiceImpl extends ServiceImpl<AssetCuratedMapper, AssetCurated> implements AssetCuratedService {
}

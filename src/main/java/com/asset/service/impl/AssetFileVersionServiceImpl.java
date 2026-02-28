package com.asset.service.impl;

import com.asset.entity.AssetFileVersion;
import com.asset.mapper.AssetFileVersionMapper;
import com.asset.service.AssetFileVersionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AssetFileVersionServiceImpl extends ServiceImpl<AssetFileVersionMapper, AssetFileVersion> implements AssetFileVersionService {
}

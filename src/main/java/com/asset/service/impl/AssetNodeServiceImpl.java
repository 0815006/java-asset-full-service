package com.asset.service.impl;

import com.asset.entity.AssetNode;
import com.asset.mapper.AssetNodeMapper;
import com.asset.service.AssetNodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AssetNodeServiceImpl extends ServiceImpl<AssetNodeMapper, AssetNode> implements AssetNodeService {
}

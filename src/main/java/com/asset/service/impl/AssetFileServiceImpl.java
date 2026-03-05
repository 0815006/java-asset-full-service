package com.asset.service.impl;

import com.asset.entity.AssetFile;
import com.asset.mapper.AssetFileMapper;
import com.asset.service.AssetFileService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AssetFileServiceImpl extends ServiceImpl<AssetFileMapper, AssetFile> implements AssetFileService {

    @Override
    public void updateTreePath(String oldPath, String newPath) {
        this.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AssetFile>()
            .likeRight(AssetFile::getTreePath, oldPath))
            .forEach(file -> {
                String path = file.getTreePath();
                if (path.startsWith(oldPath)) {
                    file.setTreePath(newPath + path.substring(oldPath.length()));
                    this.updateById(file);
                }
            });
    }
}

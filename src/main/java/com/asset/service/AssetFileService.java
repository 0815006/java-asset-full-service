package com.asset.service;

import com.asset.entity.AssetFile;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AssetFileService extends IService<AssetFile> {
    void updateTreePath(String oldPath, String newPath);
}

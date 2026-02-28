package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetFileVersion;
import com.asset.service.AssetFileVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/asset-file-version")
public class AssetFileVersionController {

    @Autowired
    private AssetFileVersionService assetFileVersionService;

    @GetMapping("/list")
    public Result<List<AssetFileVersion>> list() {
        return Result.success(assetFileVersionService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody AssetFileVersion assetFileVersion) {
        return Result.success(assetFileVersionService.save(assetFileVersion));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody AssetFileVersion assetFileVersion) {
        return Result.success(assetFileVersionService.updateById(assetFileVersion));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(assetFileVersionService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<AssetFileVersion> getById(@PathVariable Long id) {
        return Result.success(assetFileVersionService.getById(id));
    }
}

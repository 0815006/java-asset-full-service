package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetNode;
import com.asset.service.AssetNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/asset-node")
public class AssetNodeController {

    @Autowired
    private AssetNodeService assetNodeService;

    @GetMapping("/list")
    public Result<List<AssetNode>> list() {
        return Result.success(assetNodeService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody AssetNode assetNode) {
        return Result.success(assetNodeService.save(assetNode));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody AssetNode assetNode) {
        return Result.success(assetNodeService.updateById(assetNode));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(assetNodeService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<AssetNode> getById(@PathVariable Long id) {
        return Result.success(assetNodeService.getById(id));
    }
}

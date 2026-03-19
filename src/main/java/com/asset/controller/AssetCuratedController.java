package com.asset.controller;

import com.asset.common.Result;
import com.asset.service.IAssetCuratedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/assets/curated")
@CrossOrigin
public class AssetCuratedController {

    @Autowired
    private IAssetCuratedService assetCuratedService;

    @PostMapping
    public Result<?> toggleCuratedStatus(@RequestBody Map<String, Object> payload) {
        Long fileId = Long.valueOf(payload.get("fileId").toString());
        Long productId = Long.valueOf(payload.get("productId").toString());
        boolean isCurated = (boolean) payload.get("isCurated");

        assetCuratedService.toggleCuratedStatus(fileId, productId, isCurated);
        return Result.success();
    }

    @GetMapping("/status")
    public Result<Map<String, Boolean>> getCuratedStatus(@RequestParam Long fileId, @RequestParam Long productId) {
        log.info("Checking curated status for fileId: {}, productId: {}", fileId, productId);
        try {
            boolean isCurated = assetCuratedService.isCurated(fileId, productId);
            log.info("Successfully checked curated status for fileId: {}, productId: {}. Status: {}", fileId, productId, isCurated);
            return Result.success(Collections.singletonMap("isCurated", isCurated));
        } catch (Exception e) {
            log.error("Failed to check curated status for fileId: {}, productId: {}", fileId, productId, e);
            return Result.error("查询核心状态失败，请检查后台日志");
        }
    }
}

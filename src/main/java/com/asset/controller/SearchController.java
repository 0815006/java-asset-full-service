package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.entity.Product;
import com.asset.service.AssetFileService;
import com.asset.service.ProductService;
import com.asset.service.SearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/search")
@CrossOrigin
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private AssetFileService assetFileService;

    @Autowired
    private ProductService productService;

    @GetMapping
    public Result<List<Map<String, Object>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String zoneType,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Result<List<Map<String, Object>>> searchResult = searchService.search(keyword, zoneType, productId, page, size);
        
        if (searchResult.getCode() == 200) {
            List<Map<String, Object>> results = searchResult.getData();
            
            if (results != null) {
                // 提取所有 product_id
                List<Long> productIds = results.stream()
                    .filter(doc -> "product".equals(doc.get("zone_type")) && doc.get("product_id") != null)
                    .map(doc -> {
                        try {
                            return Long.parseLong(doc.get("product_id").toString());
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

                if (!productIds.isEmpty()) {
                    // 批量查询产品信息
                    Map<Long, Product> productMap = productService.listByIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity(), (existing, replacement) -> existing));

                    // 回填产品名称
                    results.forEach(doc -> {
                        if ("product".equals(doc.get("zone_type")) && doc.get("product_id") != null) {
                            try {
                                Long pid = Long.parseLong(doc.get("product_id").toString());
                                Product product = productMap.get(pid);
                                if (product != null) {
                                    doc.put("zone_name", product.getProductName());
                                } else {
                                    doc.put("zone_name", "未知产品");
                                }
                            } catch (NumberFormatException e) {
                                doc.put("zone_name", "未知产品");
                            }
                        }
                    });
                }
            }
        }
        
        return searchResult;
    }

    @GetMapping("/health-check")
    public Result<Map<String, Object>> healthCheck() {
        // 1. 获取数据库中所有最新版本的文件
        List<AssetFile> dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getIsLatest, 1)
                .eq(AssetFile::getNodeType, 2));

        // 2. 获取 Solr 中所有文档
        List<Map<String, Object>> solrDocs = searchService.getAllIndexedDocuments();

        System.out.println("索引健康检查：DB文件数=" + dbFiles.size() + ", Solr文档数=" + solrDocs.size());

        Map<String, Object> result = new HashMap<>();
        result.put("dbCount", dbFiles.size());
        result.put("solrCount", solrDocs.size());
        result.put("dbFiles", dbFiles);
        result.put("solrDocs", solrDocs);

        return Result.success(result);
    }

    @PostMapping("/reindex/{id}")
    public Result<Void> reindex(@PathVariable Long id) {
        AssetFile file = assetFileService.getById(id);
        if (file != null) {
            searchService.index(file);
        }
        return Result.success();
    }

    @DeleteMapping("/index/{solrId}")
    public Result<Void> deleteIndex(@PathVariable String solrId) {
        searchService.deleteBySolrId(solrId);
        return Result.success();
    }

    @PostMapping("/rebuild-all/start")
    public Result<Void> startRebuildAll() {
        searchService.startRebuildAll();
        return Result.success();
    }

    @GetMapping("/rebuild-all/progress")
    public Result<Map<String, Object>> getRebuildProgress() {
        return Result.success(searchService.getRebuildProgress());
    }
}

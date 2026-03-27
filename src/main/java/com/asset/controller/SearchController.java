package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.entity.Product;
import com.asset.entity.AssetHotSearch;
import com.asset.service.AssetFileService;
import com.asset.service.ProductService;
import com.asset.service.SearchService;
import com.asset.service.AssetHotSearchService;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 资产搜索控制器
 * 处理全文检索、热门搜索词、索引重建及健康检查
 */
@RestController
@RequestMapping("/api/search")
@CrossOrigin
public class SearchController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private AssetFileService assetFileService;

    @Autowired
    private ProductService productService;

    @Autowired
    private AssetHotSearchService assetHotSearchService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 执行全文搜索
     * @param keyword 关键词
     * @param zoneType 专区类型
     * @param productId 产品 ID
     * @param page 页码
     * @param size 每页大小
     * @return 搜索结果列表
     */
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

        // 更新热门搜索词
        if (keyword != null && !keyword.trim().isEmpty()) {
            String trimmedKeyword = keyword.trim();
            // 限制热门词长度，超过 15 个字符不计入热门统计
            if (trimmedKeyword.length() <= 15) {
                LambdaQueryWrapper<AssetHotSearch> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(AssetHotSearch::getKeyword, trimmedKeyword);
                AssetHotSearch hotSearch = assetHotSearchService.getOne(wrapper);

                if (hotSearch == null) {
                    hotSearch = new AssetHotSearch();
                    hotSearch.setKeyword(trimmedKeyword);
                    hotSearch.setSearchCount(1);
                    hotSearch.setIsActive(true);
                    hotSearch.setUpdatedAt(java.time.LocalDateTime.now());
                    assetHotSearchService.save(hotSearch);
                } else {
                    hotSearch.setSearchCount(hotSearch.getSearchCount() + 1);
                    hotSearch.setUpdatedAt(java.time.LocalDateTime.now());
                    assetHotSearchService.updateById(hotSearch);
                }
            }
        }

        return searchResult;
    }

    /**
     * 获取热门搜索关键词
     * @param limit 返回数量限制
     * @return 热门关键词列表
     */
    @GetMapping("/hot-keywords")
    public Result<List<AssetHotSearch>> getHotKeywords(@RequestParam(defaultValue = "10") int limit) {
        LambdaQueryWrapper<AssetHotSearch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AssetHotSearch::getIsActive, true);
        wrapper.orderByDesc(AssetHotSearch::getSearchCount);
        wrapper.last("LIMIT " + limit);
        List<AssetHotSearch> hotSearches = assetHotSearchService.list(wrapper);
        return Result.success(hotSearches);
    }

    /**
     * 索引健康检查：比对数据库文件记录与 Solr 索引文档的一致性
     */
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

    /**
     * 重新索引指定文件
     * @param id 文件 ID
     */
    @PostMapping("/reindex/{id}")
    public Result<Void> reindex(@PathVariable Long id) {
        AssetFile file = assetFileService.getById(id);
        if (file != null) {
            searchService.index(file);
        }
        return Result.success();
    }

    /**
     * 删除指定 Solr 索引文档
     * @param solrId Solr 文档 ID
     */
    @DeleteMapping("/index/{solrId}")
    public Result<Void> deleteIndex(@PathVariable String solrId) {
        searchService.deleteBySolrId(solrId);
        return Result.success();
    }

    /**
     * 启动全量索引重建任务
     */
    @PostMapping("/rebuild-all/start")
    public Result<Void> startRebuildAll() {
        searchService.startRebuildAll();
        return Result.success();
    }

    /**
     * 获取全局使用频率最高的文件排行（从 Redis 获取）
     */
    @GetMapping("/global-use-top")
    public Result<List<Map<String, Object>>> getGlobalUseTop() {
        String json = stringRedisTemplate.opsForValue().get("global_use_top");
        if (json != null) {
            return Result.success(com.alibaba.fastjson.JSON.parseObject(json, new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {}));
        }
        return Result.success(Collections.emptyList());
    }

    /**
     * 获取全局收藏频率最高的文件排行（从 Redis 获取）
     */
    @GetMapping("/global-star-top")
    public Result<List<Map<String, Object>>> getGlobalStarTop() {
        String json = stringRedisTemplate.opsForValue().get("global_star_top");
        if (json != null) {
            return Result.success(com.alibaba.fastjson.JSON.parseObject(json, new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {}));
        }
        return Result.success(Collections.emptyList());
    }

    /**
     * 获取全量索引重建进度
     */
    @GetMapping("/rebuild-all/progress")
    public Result<Map<String, Object>> getRebuildProgress() {
        return Result.success(searchService.getRebuildProgress());
    }
}

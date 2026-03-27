package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.Product;
import com.asset.entity.UserFavoriteProduct;
import com.asset.entity.User;
import com.asset.service.ProductService;
import com.asset.service.UserFavoriteProductService;
import com.asset.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.asset.entity.AssetFile;
import com.asset.service.AssetFileService;
import com.asset.service.SearchService;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * 产品管理控制器
 * 处理产品的增删改查、初始化目录及收藏逻辑
 */
@RestController
@RequestMapping("/api/products")
@CrossOrigin
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private UserFavoriteProductService userFavoriteProductService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private AssetFileService assetFileService;

    @Autowired
    private SearchService searchService;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 获取产品列表
     * @param userId 当前用户 ID（用于判断收藏状态）
     * @return 产品列表
     */
    @GetMapping("/list")
    public Result<List<Product>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        List<Product> products = productService.list();
        
        // Populate ownerName
        List<Long> userIds = products.stream().map(Product::getOwnerId).distinct().collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            Map<Long, String> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
            products.forEach(p -> p.setOwnerName(userMap.get(p.getOwnerId())));
        }
        
        if (userId != null) {
            List<UserFavoriteProduct> favorites = userFavoriteProductService.list(
                new LambdaQueryWrapper<UserFavoriteProduct>().eq(UserFavoriteProduct::getUserId, userId)
            );
            List<Long> favProductIds = favorites.stream().map(UserFavoriteProduct::getProductId).collect(Collectors.toList());
            
            products.forEach(p -> {
                p.setIsFavorited(favProductIds.contains(p.getId()));
                
                // Mock permission logic
                Map<String, Boolean> permission = new HashMap<>();
                permission.put("can_upload", true); // Simplified
                permission.put("can_manage_folder", true);
                p.setCurrentUserPermission(permission);
            });
        }
        
        return Result.success(products);
    }

    /**
     * 创建新产品
     * @param product 产品实体
     * @return 创建后的产品
     */
    @PostMapping
    public Result<Product> create(@RequestBody Product product) {
        product.setAssetCount(0);
        productService.save(product);
        return Result.success(product);
    }

    /**
     * 更新产品信息
     * @param id 产品 ID
     * @param product 产品实体
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productService.updateById(product);
        return Result.success();
    }

    /**
     * 初始化产品的基础目录结构
     * @param id 产品 ID
     * @return 操作结果消息
     */
    @PostMapping("/{id}/init-folders")
    public Result<String> initFolders(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) return Result.error("产品不存在");

        List<String> folderNames = Arrays.asList(
            "0.产品功能全景及功能测试指南",
            "1.产品架构及关联产品",
            "2.产品缺陷分析",
            "3.产品业务知识",
            "4.产品其他支持类文档"
        );

        int createdCount = 0;
        int existedCount = 0;
        int repairedCount = 0;

        for (String name : folderNames) {
            // 1. 检查数据库记录是否存在
            long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getProductId, id)
                    .eq(AssetFile::getParentId, 0)
                    .eq(AssetFile::getFileName, name)
                    .eq(AssetFile::getNodeType, 1)
                    .eq(AssetFile::getIsLatest, 1));
            
            // 2. 确定物理路径 (mkdirs 支持多级目录创建，兼容 Windows/Linux)
            String physicalPath = uploadDir + "/product_zone/" + id + "/" + name;
            java.io.File dir = new java.io.File(physicalPath.replace("//", "/"));
            
            // 3. 如果物理目录不存在，则补充创建 (解决磁盘目录丢失问题)
            boolean diskMissing = !dir.exists();
            if (diskMissing) {
                dir.mkdirs();
            }

            if (count > 0) {
                existedCount++;
                if (diskMissing) {
                    repairedCount++;
                }
                continue;
            }

            // 4. 如果数据库记录不存在，则创建记录
            AssetFile folder = new AssetFile();
            folder.setProductId(id);
            folder.setParentId(0L);
            folder.setFileName(name);
            folder.setNodeType(1); // 文件夹
            folder.setIsLatest(1);
            folder.setVersionNo(1);
            folder.setParseStatus(0);
            folder.setCreatedAt(java.time.LocalDateTime.now());
            folder.setUpdatedAt(java.time.LocalDateTime.now());
            assetFileService.save(folder);

            // 更新 treePath
            folder.setTreePath("/0/" + folder.getId() + "/");
            assetFileService.updateById(folder);

            createdCount++;
        }

        if (createdCount == 0 && existedCount > 0 && repairedCount == 0) {
            return Result.error("基础目录已存在，无需重复创建");
        }

        StringBuilder msg = new StringBuilder();
        msg.append("成功创建 ").append(createdCount).append(" 个目录");
        if (existedCount > 0) {
            msg.append("，").append(existedCount).append(" 个已存在");
        }
        if (repairedCount > 0) {
            msg.append(" (其中修复了 ").append(repairedCount).append(" 个丢失的磁盘目录)");
        }

        return Result.success(msg.toString());
    }

    /**
     * 收藏或取消收藏产品
     * @param productId 产品 ID
     * @param body 包含 action (1=收藏, 0=取消)
     * @param userId 当前用户 ID
     * @return 操作结果
     */
    @PostMapping("/{productId}/favorite")
    public Result<Void> favorite(@PathVariable Long productId, @RequestBody Map<String, Integer> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;
        
        Integer action = body.get("action"); // 1=fav, 0=unfav
        
        if (action == 1) {
            UserFavoriteProduct fav = new UserFavoriteProduct();
            fav.setUserId(currentUserId);
            fav.setProductId(productId);
            try {
                userFavoriteProductService.save(fav);
            } catch (Exception e) {
                // Ignore duplicate key error
            }
        } else {
            userFavoriteProductService.remove(
                new LambdaQueryWrapper<UserFavoriteProduct>()
                    .eq(UserFavoriteProduct::getUserId, currentUserId)
                    .eq(UserFavoriteProduct::getProductId, productId)
            );
        }
        
        return Result.success();
    }

    /**
     * 初始化专区（测试技术与工艺专区、管理专区）的基础目录结构
     * @param body 包含 type (tech_zone 或 mgmt_zone)
     * @param userId 当前用户 ID
     * @return 操作结果消息
     */
    @PostMapping("/init-zone-folders")
    public Result<String> initZoneFolders(@RequestBody Map<String, String> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String type = body.get("type");
        if (type == null || (!type.equals("tech_zone") && !type.equals("mgmt_zone"))) {
            return Result.error("无效的专区类型");
        }

        // 1. 查找专区根节点 (parentId = 0, 名称包含关键字)
        List<AssetFile> zoneRoots = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getParentId, 0)
                .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理")
                .last("limit 1"));
        
        if (zoneRoots.isEmpty()) {
            return Result.error("未找到专区根节点，请先初始化基础数据");
        }
        AssetFile zoneRoot = zoneRoots.get(0);

        // 2. 定义目录列表
        List<String> folderNames;
        if (type.equals("tech_zone")) {
            folderNames = Arrays.asList(
                "通用测试点", "信创测试", "质量专题", "测试案例分级", "测试数据集",
                "非功能测试", "性能测试", "自动化测试", "稳定性测试", "智能化测试"
            );
        } else {
            folderNames = Arrays.asList("测试任务管理", "批次管理");
        }

        int createdCount = 0;
        int existedCount = 0;
        int repairedCount = 0;
        Long currentUserId = userId != null ? userId : 2L;

        for (String name : folderNames) {
            // 3. 检查数据库记录是否存在
            long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getProductId, 0) // 专区的产品ID固定为0
                    .eq(AssetFile::getParentId, zoneRoot.getId())
                    .eq(AssetFile::getFileName, name)
                    .eq(AssetFile::getNodeType, 1)
                    .eq(AssetFile::getIsLatest, 1));
            
            // 4. 确定物理路径 (直接在专区目录下创建，不包含专区根节点名称)
            String physicalPath = uploadDir + "/" + type + "/" + name;
            java.io.File dir = new java.io.File(physicalPath.replace("//", "/"));
            
            // 5. 如果物理目录不存在，则补充创建
            boolean diskMissing = !dir.exists();
            if (diskMissing) {
                dir.mkdirs();
            }

            if (count > 0) {
                existedCount++;
                if (diskMissing) {
                    repairedCount++;
                }
                continue;
            }

            // 6. 如果数据库记录不存在，则创建记录
            AssetFile folder = new AssetFile();
            folder.setProductId(0L);
            folder.setParentId(zoneRoot.getId());
            folder.setFileName(name);
            folder.setNodeType(1); // 文件夹
            folder.setIsLatest(1);
            folder.setVersionNo(1);
            folder.setParseStatus(0);
            folder.setCreatedBy(currentUserId);
            folder.setCreatedAt(java.time.LocalDateTime.now());
            folder.setUpdatedAt(java.time.LocalDateTime.now());
            assetFileService.save(folder);

            // 更新 treePath
            folder.setTreePath(zoneRoot.getTreePath() + folder.getId() + "/");
            assetFileService.updateById(folder);

            createdCount++;
        }

        if (createdCount == 0 && existedCount > 0 && repairedCount == 0) {
            return Result.error("基础目录已存在，无需重复创建");
        }

        StringBuilder msg = new StringBuilder();
        msg.append("成功创建 ").append(createdCount).append(" 个目录");
        if (existedCount > 0) {
            msg.append("，").append(existedCount).append(" 个已存在");
        }
        if (repairedCount > 0) {
            msg.append(" (其中修复了 ").append(repairedCount).append(" 个丢失的磁盘目录)");
        }

        return Result.success(msg.toString());
    }
}

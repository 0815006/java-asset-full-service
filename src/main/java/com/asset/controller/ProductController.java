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

    @PostMapping
    public Result<Product> create(@RequestBody Product product) {
        product.setAssetCount(0);
        productService.save(product);
        return Result.success(product);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        productService.updateById(product);
        return Result.success();
    }

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

        for (String name : folderNames) {
            // 检查数据库是否已存在
            long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getProductId, id)
                    .eq(AssetFile::getParentId, 0)
                    .eq(AssetFile::getFileName, name)
                    .eq(AssetFile::getNodeType, 1)
                    .eq(AssetFile::getIsLatest, 1));
            
            if (count > 0) {
                existedCount++;
                continue;
            }

            // 创建数据库记录
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

            // 创建物理目录
            String physicalPath = uploadDir + "/product_zone/" + id + "/" + name;
            java.io.File dir = new java.io.File(physicalPath.replace("//", "/"));
            if (!dir.exists()) {
                dir.mkdirs();
            }

            createdCount++;
        }

        if (createdCount == 0 && existedCount > 0) {
            return Result.error("基础目录已存在，无需重复创建");
        }

        return Result.success("成功创建 " + createdCount + " 个目录" + (existedCount > 0 ? "，" + existedCount + " 个已存在" : ""));
    }

    @PostMapping("/{productId}/favorite")
    public Result<Void> favorite(@PathVariable Long productId, @RequestBody Map<String, Integer> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) {
            // For demo, default to user 2 (chendong) if not provided
            userId = 2L; 
        }
        
        Integer action = body.get("action"); // 1=fav, 0=unfav
        
        if (action == 1) {
            UserFavoriteProduct fav = new UserFavoriteProduct();
            fav.setUserId(userId);
            fav.setProductId(productId);
            try {
                userFavoriteProductService.save(fav);
            } catch (Exception e) {
                // Ignore duplicate key error
            }
        } else {
            userFavoriteProductService.remove(
                new LambdaQueryWrapper<UserFavoriteProduct>()
                    .eq(UserFavoriteProduct::getUserId, userId)
                    .eq(UserFavoriteProduct::getProductId, productId)
            );
        }
        
        return Result.success();
    }
}

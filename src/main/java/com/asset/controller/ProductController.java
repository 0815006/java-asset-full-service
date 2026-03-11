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

@RestController
@RequestMapping("/products")
@CrossOrigin
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private UserFavoriteProductService userFavoriteProductService;
    
    @Autowired
    private UserService userService;

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

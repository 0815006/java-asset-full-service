package com.asset.controller;

import com.asset.common.Result;
import com.asset.dto.BusiProductDTO;
import com.asset.dto.ProductQueryDTO;
import com.asset.entity.BusiProduct;
import com.asset.service.BusiProductService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/busi-product")
public class BusiProductController {

    @Autowired
    private BusiProductService busiProductService;

    @GetMapping("/list")
    public Result<IPage<BusiProductDTO>> list(ProductQueryDTO query) {
        return Result.success(busiProductService.getProductList(query));
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody BusiProduct busiProduct) {
        return Result.success(busiProductService.save(busiProduct));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody BusiProduct busiProduct) {
        return Result.success(busiProductService.updateById(busiProduct));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(busiProductService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<BusiProduct> getById(@PathVariable Long id) {
        return Result.success(busiProductService.getById(id));
    }
}

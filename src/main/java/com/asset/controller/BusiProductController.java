package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.BusiProduct;
import com.asset.service.BusiProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/busi-product")
public class BusiProductController {

    @Autowired
    private BusiProductService busiProductService;

    @GetMapping("/list")
    public Result<List<BusiProduct>> list() {
        return Result.success(busiProductService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody BusiProduct busiProduct) {
        return Result.success(busiProductService.save(busiProduct));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody BusiProduct busiProduct) {
        return Result.success(busiProductService.updateById(busiProduct));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(busiProductService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<BusiProduct> getById(@PathVariable Long id) {
        return Result.success(busiProductService.getById(id));
    }
}

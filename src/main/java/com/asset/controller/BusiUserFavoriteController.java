package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.BusiUserFavorite;
import com.asset.service.BusiUserFavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/busi-user-favorite")
public class BusiUserFavoriteController {

    @Autowired
    private BusiUserFavoriteService busiUserFavoriteService;

    @GetMapping("/list")
    public Result<List<BusiUserFavorite>> list() {
        return Result.success(busiUserFavoriteService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody BusiUserFavorite busiUserFavorite) {
        return Result.success(busiUserFavoriteService.save(busiUserFavorite));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody BusiUserFavorite busiUserFavorite) {
        return Result.success(busiUserFavoriteService.updateById(busiUserFavorite));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(busiUserFavoriteService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<BusiUserFavorite> getById(@PathVariable Long id) {
        return Result.success(busiUserFavoriteService.getById(id));
    }
}

package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.SysDomain;
import com.asset.service.SysDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys-domain")
public class SysDomainController {

    @Autowired
    private SysDomainService sysDomainService;

    @GetMapping("/list")
    public Result<List<SysDomain>> list() {
        return Result.success(sysDomainService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody SysDomain sysDomain) {
        return Result.success(sysDomainService.save(sysDomain));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody SysDomain sysDomain) {
        return Result.success(sysDomainService.updateById(sysDomain));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(sysDomainService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<SysDomain> getById(@PathVariable Long id) {
        return Result.success(sysDomainService.getById(id));
    }
}

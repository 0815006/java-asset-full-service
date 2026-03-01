package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.SysTeam;
import com.asset.service.SysTeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys-team")
public class SysTeamController {

    @Autowired
    private SysTeamService sysTeamService;

    @GetMapping("/list")
    public Result<List<SysTeam>> list() {
        return Result.success(sysTeamService.list());
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody SysTeam sysTeam) {
        return Result.success(sysTeamService.save(sysTeam));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody SysTeam sysTeam) {
        return Result.success(sysTeamService.updateById(sysTeam));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(sysTeamService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<SysTeam> getById(@PathVariable Long id) {
        return Result.success(sysTeamService.getById(id));
    }
}

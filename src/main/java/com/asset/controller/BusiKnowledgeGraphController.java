package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.BusiKnowledgeGraph;
import com.asset.service.BusiKnowledgeGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/busi-knowledge-graph")
public class BusiKnowledgeGraphController {

    @Autowired
    private BusiKnowledgeGraphService busiKnowledgeGraphService;

    @GetMapping("/list")
    public Result<List<BusiKnowledgeGraph>> list() {
        return Result.success(busiKnowledgeGraphService.list());
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody BusiKnowledgeGraph busiKnowledgeGraph) {
        return Result.success(busiKnowledgeGraphService.save(busiKnowledgeGraph));
    }

    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody BusiKnowledgeGraph busiKnowledgeGraph) {
        return Result.success(busiKnowledgeGraphService.updateById(busiKnowledgeGraph));
    }

    @DeleteMapping("/delete/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(busiKnowledgeGraphService.removeById(id));
    }

    @GetMapping("/{id}")
    public Result<BusiKnowledgeGraph> getById(@PathVariable Long id) {
        return Result.success(busiKnowledgeGraphService.getById(id));
    }
}

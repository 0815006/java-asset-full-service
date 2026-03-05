package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.service.AssetFileService;
import com.asset.service.SearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
@CrossOrigin
public class AssetFileController {

    @Autowired
    private AssetFileService assetFileService;
    
    @Autowired
    private SearchService searchService;

    @GetMapping("/tree")
    public Result<List<AssetFile>> getTree(@RequestParam("product_id") Long productId, @RequestParam("parent_id") Long parentId) {
        List<AssetFile> list = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getParentId, parentId)
                .eq(AssetFile::getIsLatest, 1)
                .orderByAsc(AssetFile::getNodeType) // Folders first
                .orderByAsc(AssetFile::getId));

        list.forEach(node -> {
            // Check if has children (for lazy loading)
            if (node.getNodeType() == 1) {
                long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getParentId, node.getId())
                        .eq(AssetFile::getIsLatest, 1));
                node.setHasChildren(count > 0);
            } else {
                node.setHasChildren(false);
            }
            
            // Mock permission
            Map<String, Boolean> permission = new HashMap<>();
            permission.put("can_upload", true);
            permission.put("can_delete", true);
            node.setCurrentUserPermission(permission);
        });

        return Result.success(list);
    }

    @PostMapping("/folder")
    public Result<AssetFile> createFolder(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("product_id").toString());
        Long parentId = Long.valueOf(body.get("parent_id").toString());
        String folderName = (String) body.get("folder_name");

        AssetFile folder = new AssetFile();
        folder.setProductId(productId);
        folder.setParentId(parentId);
        folder.setFileName(folderName);
        folder.setNodeType(1); // Folder
        folder.setCreatedBy(1L); // Mock user
        
        assetFileService.save(folder);
        
        // Update tree path
        String parentPath = "/0/";
        if (parentId != 0) {
            AssetFile parent = assetFileService.getById(parentId);
            if (parent != null) {
                parentPath = parent.getTreePath();
            }
        }
        folder.setTreePath(parentPath + folder.getId() + "/");
        assetFileService.updateById(folder);

        return Result.success(folder);
    }

    @PutMapping("/{id}/rename")
    public Result<Void> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newName = body.get("new_name");
        AssetFile file = assetFileService.getById(id);
        if (file != null) {
            file.setFileName(newName);
            assetFileService.updateById(file);
        }
        return Result.success();
    }
    
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        assetFileService.removeById(id);
        searchService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/move")
    public Result<Map<String, String>> move(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long targetParentId = body.get("target_parent_id");
        AssetFile file = assetFileService.getById(id);
        if (file == null) return Result.error("File not found");

        String oldPath = file.getTreePath();
        
        // Calculate new path prefix
        String newParentPath = "/0/";
        if (targetParentId != 0) {
            AssetFile parent = assetFileService.getById(targetParentId);
            if (parent != null) {
                newParentPath = parent.getTreePath();
            }
        }
        
        String newPath = newParentPath + file.getId() + "/";
        
        // Update current node
        file.setParentId(targetParentId);
        file.setTreePath(newPath);
        assetFileService.updateById(file);
        
        // Recursive update for children
        if (file.getNodeType() == 1) { // Folder
             assetFileService.updateTreePath(oldPath, newPath);
        }
        
        Map<String, String> res = new HashMap<>();
        res.put("new_tree_path", newPath);
        return Result.success(res);
    }

    @PostMapping("/upload")
    public Result<AssetFile> upload(@RequestParam("file") MultipartFile file, 
                                    @RequestParam("product_id") Long productId, 
                                    @RequestParam("parent_id") Long parentId) {
        // Mock upload logic
        String fileName = file.getOriginalFilename();
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        
        // Check for existing file to version
        AssetFile existing = assetFileService.getOne(new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getProductId, productId)
            .eq(AssetFile::getParentId, parentId)
            .eq(AssetFile::getFileName, fileName)
            .eq(AssetFile::getIsLatest, 1));
            
        int version = 1;
        if (existing != null) {
            existing.setIsLatest(0);
            assetFileService.updateById(existing);
            version = existing.getVersionNo() + 1;
        }
        
        AssetFile newFile = new AssetFile();
        newFile.setProductId(productId);
        newFile.setParentId(parentId);
        newFile.setFileName(fileName);
        newFile.setExt(ext);
        newFile.setFileSize(file.getSize());
        newFile.setVersionNo(version);
        newFile.setIsLatest(1);
        newFile.setNodeType(2); // File
        newFile.setParseStatus(1); // Queued
        newFile.setCreatedBy(1L); // Mock user
        
        assetFileService.save(newFile);
        
        // Update tree path
        String parentPath = "/0/";
        if (parentId != 0) {
            AssetFile parent = assetFileService.getById(parentId);
            if (parent != null) {
                parentPath = parent.getTreePath();
            }
        }
        newFile.setTreePath(parentPath + newFile.getId() + "/");
        assetFileService.updateById(newFile);
        
        // Index to Solr
        searchService.index(newFile);
        
        return Result.success(newFile);
    }
    
    @GetMapping("/{id}/detail")
    public Result<AssetFile> detail(@PathVariable Long id) {
        AssetFile file = assetFileService.getById(id);
        if (file == null) return Result.error("File not found");
        
        // Mock content for MD
        if ("md".equalsIgnoreCase(file.getExt())) {
            // In real app, read from file system
        }
        
        return Result.success(file);
    }

    @PostMapping("/download")
    public void download(@RequestBody Map<String, List<Long>> body, HttpServletResponse response) {
        List<Long> ids = body.get("file_ids");
        if (ids == null || ids.isEmpty()) return;
        
        // Red Line 8: Limit check
        if (ids.size() > 50) {
            response.setStatus(400);
            try {
                response.getWriter().write("{\"code\": 400, \"message\": \"Too many files (max 50)\"}");
            } catch (IOException e) {}
            return;
        }
        
        List<AssetFile> files = assetFileService.listByIds(ids);
        long totalSize = files.stream().mapToLong(AssetFile::getFileSize).sum();
        if (totalSize > 500 * 1024 * 1024) { // 500MB
             response.setStatus(400);
             try {
                response.getWriter().write("{\"code\": 400, \"message\": \"Total size exceeds 500MB\"}");
            } catch (IOException e) {}
            return;
        }
        
        // Mock download
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"download.zip\"");
        try {
            response.getWriter().write("Mock file content");
        } catch (IOException e) {}
    }
}

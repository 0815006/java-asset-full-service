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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/assets")
@CrossOrigin
public class AssetFileController {

    @Autowired
    private AssetFileService assetFileService;
    
    @Autowired
    private SearchService searchService;

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir}")
    private String uploadDir;

    private String getPhysicalPath(AssetFile file) {
        StringBuilder path = new StringBuilder(uploadDir);
        
        // 1. Determine Zone
        String zone = "product_zone";
        Long productId = file.getProductId();
        
        if (productId == 0) {
            AssetFile root = findRootNode(file);
            if (root != null) {
                if (root.getFileName().contains("测试技术")) {
                    zone = "tech_zone";
                } else if (root.getFileName().contains("管理")) {
                    zone = "mgmt_zone";
                }
            }
        }
        
        path.append("/").append(zone);
        path.append("/").append(productId);
        
        // Ensure base zone/product directory exists
        java.io.File baseDir = new java.io.File(path.toString());
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        
        // 2. Append tree structure (excluding self)
        if (file.getTreePath() != null) {
            String[] ids = file.getTreePath().split("/");
            for (String idStr : ids) {
                if (idStr.isEmpty() || idStr.equals("0")) continue;
                Long id = Long.valueOf(idStr);
                if (id.equals(file.getId())) continue;
                
                AssetFile node = assetFileService.getById(id);
                if (node != null && node.getNodeType() == 1) {
                    path.append("/").append(node.getFileName());
                }
            }
        }
        
        return path.toString().replace("//", "/");
    }

    private AssetFile findRootNode(AssetFile node) {
        if (node.getParentId() == 0) return node;
        AssetFile parent = assetFileService.getById(node.getParentId());
        if (parent == null) return node;
        return findRootNode(parent);
    }

    @GetMapping("/storage-path")
    public Result<String> getStoragePath(@RequestParam("type") String type, @RequestParam(value = "product_id", required = false) Long productId) {
        String zone = type;
        Long pid = productId != null ? productId : 0L;
        String path = uploadDir + "/" + zone + "/" + pid;
        return Result.success(path.replace("//", "/"));
    }

    @PostMapping("/create-root-dir")
    public Result<Void> createRootDir(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        Long productId = body.get("product_id") != null ? Long.valueOf(body.get("product_id").toString()) : 0L;
        
        String pathStr = uploadDir + "/" + type + "/" + productId;
        java.io.File dir = new java.io.File(pathStr.replace("//", "/"));
        
        if (dir.exists()) {
            return Result.error("该目录已经存在");
        }
        
        boolean created = dir.mkdirs();
        if (created) {
            return Result.success();
        } else {
            return Result.error("目录创建失败，请检查权限或路径配置");
        }
    }

    @GetMapping("/health-check")
    public Result<HealthCheckNode> healthCheck(@RequestParam("type") String type, @RequestParam(value = "product_id", required = false) Long productId) {
        String zone = type; // tech_zone, mgmt_zone, product_zone
        Long pid = productId != null ? productId : 0L;
        
        // 1. Determine base physical path
        String basePhysicalPath = uploadDir + "/" + zone + "/" + pid;
        java.io.File baseDir = new java.io.File(basePhysicalPath);
        
        // 2. Get all DB records for this unit
        List<AssetFile> dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, pid)
                .eq(AssetFile::getIsLatest, 1));
        
        // If it's a public zone, we need to filter by root node
        if (pid == 0) {
            AssetFile zoneRoot = assetFileService.getOne(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getParentId, 0)
                    .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理"));
            if (zoneRoot != null) {
                dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .like(AssetFile::getTreePath, "/" + zoneRoot.getId() + "/")
                        .eq(AssetFile::getIsLatest, 1));
                // Add the root itself
                dbFiles.add(zoneRoot);
            }
        }

        // 3. Build Health Tree
        HealthCheckNode root = buildHealthTree(baseDir, dbFiles, pid, type);
        return Result.success(root);
    }

    private HealthCheckNode buildHealthTree(java.io.File dir, List<AssetFile> dbFiles, Long productId, String type) {
        HealthCheckNode node = new HealthCheckNode();
        node.setName(dir.getName());
        node.setNodeType(1);
        
        // Check if this directory exists in DB
        boolean existsInDb = dbFiles.stream().anyMatch(f -> f.getNodeType() == 1 && dir.getAbsolutePath().replace("\\", "/").endsWith(getRelativePathFromDb(f)));
        // This is complex because getPhysicalPath uses fileName which might not be unique.
        // For simplicity in this mock/demo, let's use a name-based matching for the tree structure.
        
        // Let's use a better approach: Start from DB tree and check physical, then add extra physical files.
        return performFullCheck(dir, dbFiles, productId, type);
    }

    private HealthCheckNode performFullCheck(java.io.File physicalDir, List<AssetFile> dbFiles, Long productId, String type) {
        // 1. Find the DB root for this physical dir
        // For the very first call, physicalDir is the zone/product root.
        HealthCheckNode root = new HealthCheckNode();
        root.setName(type.equals("product_zone") ? "产品专区-" + productId : (type.contains("tech") ? "测试技术与工艺专区" : "管理专区"));
        root.setNodeType(1);
        root.setStatus("normal");

        // Map DB files by parentId for easy traversal
        Map<Long, List<AssetFile>> dbMap = dbFiles.stream().collect(Collectors.groupingBy(AssetFile::getParentId));
        
        // Find the starting parentId
        Long startParentId = 0L;
        if (productId == 0) {
             AssetFile zoneRoot = dbFiles.stream().filter(f -> f.getParentId() == 0).findFirst().orElse(null);
             if (zoneRoot != null) {
                 // We start from the children of the zone root
                 startParentId = zoneRoot.getId();
                 root.setName(zoneRoot.getFileName());
             }
        }

        checkRecursive(root, physicalDir, startParentId, dbMap);
        
        return root;
    }

    private void checkRecursive(HealthCheckNode parentNode, java.io.File physicalDir, Long dbParentId, Map<Long, List<AssetFile>> dbMap) {
        List<AssetFile> dbChildren = dbMap.getOrDefault(dbParentId, new ArrayList<>());
        java.io.File[] physicalChildren = physicalDir.exists() ? physicalDir.listFiles() : new java.io.File[0];
        if (physicalChildren == null) physicalChildren = new java.io.File[0];

        Set<String> processedPhysicalNames = new HashSet<>();

        // 1. Check DB records against Physical
        for (AssetFile dbFile : dbChildren) {
            HealthCheckNode childNode = new HealthCheckNode();
            childNode.setName(dbFile.getFileName());
            childNode.setNodeType(dbFile.getNodeType());
            
            java.io.File physicalFile = new java.io.File(physicalDir, dbFile.getFileName());
            if (physicalFile.exists()) {
                childNode.setStatus("normal");
                processedPhysicalNames.add(dbFile.getFileName());
                if (dbFile.getNodeType() == 1) {
                    checkRecursive(childNode, physicalFile, dbFile.getId(), dbMap);
                }
            } else {
                childNode.setStatus("missing");
            }
            parentNode.getChildren().add(childNode);
        }

        // 2. Check Physical files not in DB (Extra)
        for (java.io.File pFile : physicalChildren) {
            if (processedPhysicalNames.contains(pFile.getName())) continue;
            
            HealthCheckNode extraNode = new HealthCheckNode();
            extraNode.setName(pFile.getName());
            extraNode.setNodeType(pFile.isDirectory() ? 1 : 2);
            extraNode.setStatus("extra");
            
            if (pFile.isDirectory()) {
                addExtraRecursive(extraNode, pFile);
            }
            parentNode.getChildren().add(extraNode);
        }
    }

    private void addExtraRecursive(HealthCheckNode parentNode, java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            HealthCheckNode child = new HealthCheckNode();
            child.setName(f.getName());
            child.setNodeType(f.isDirectory() ? 1 : 2);
            child.setStatus("extra");
            if (f.isDirectory()) {
                addExtraRecursive(child, f);
            }
            parentNode.getChildren().add(child);
        }
    }

    public static class HealthCheckNode {
        private String name;
        private Integer nodeType;
        private String status;
        private List<HealthCheckNode> children = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getNodeType() { return nodeType; }
        public void setNodeType(Integer nodeType) { this.nodeType = nodeType; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<HealthCheckNode> getChildren() { return children; }
        public void setChildren(List<HealthCheckNode> children) { this.children = children; }
    }

    private String getRelativePathFromDb(AssetFile file) {
        // Helper to get relative path for matching
        return ""; // Simplified for now
    }

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

                long subFolderCount = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getParentId, node.getId())
                        .eq(AssetFile::getNodeType, 1) // Only folders
                        .eq(AssetFile::getIsLatest, 1));
                node.setSubFolderFlag(subFolderCount > 0 ? 1 : 0);
            } else {
                node.setHasChildren(false);
                node.setSubFolderFlag(0);
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

        // Check for duplicate folder name
        long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getParentId, parentId)
                .eq(AssetFile::getFileName, folderName)
                .eq(AssetFile::getNodeType, 1) // Folder
                .eq(AssetFile::getIsLatest, 1));
        
        if (count > 0) {
            return Result.error("当前目录下已存在同名目录");
        }

        AssetFile folder = new AssetFile();
        folder.setProductId(productId);
        folder.setParentId(parentId);
        folder.setFileName(folderName);
        folder.setNodeType(1); // Folder
        folder.setIsLatest(1);
        folder.setVersionNo(1);
        folder.setParseStatus(0); // 无需解析
        folder.setCreatedBy(2L); // 陈东
        folder.setCreatedAt(java.time.LocalDateTime.now());
        folder.setUpdatedAt(java.time.LocalDateTime.now());
        
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

        // Create Physical Directory with strict check
        String parentPhysicalPath = getPhysicalPath(folder);
        java.io.File parentDir = new java.io.File(parentPhysicalPath);
        if (!parentDir.exists()) {
            // If parent directory doesn't exist in storage, we should error out as per user request
            return Result.error("存储结构异常：父级目录在物理存储中不存在");
        }

        java.io.File currentDir = new java.io.File(parentDir, folderName);
        if (!currentDir.exists()) {
            boolean created = currentDir.mkdir();
            if (!created) {
                return Result.error("物理目录创建失败");
            }
        }

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
        newFile.setCreatedBy(2L); // 陈东
        newFile.setCreatedAt(java.time.LocalDateTime.now());
        newFile.setUpdatedAt(java.time.LocalDateTime.now());
        
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
        
        // Physical Save with strict check
        try {
            String physicalDir = getPhysicalPath(newFile);
            java.io.File dir = new java.io.File(physicalDir);
            if (!dir.exists()) {
                return Result.error("存储结构异常：目标目录在物理存储中不存在");
            }
            java.io.File dest = new java.io.File(dir, fileName);
            file.transferTo(dest);
            
            newFile.setLocalPath(dest.getAbsolutePath());
            assetFileService.updateById(newFile);
        } catch (java.io.IOException e) {
            return Result.error("文件保存失败: " + e.getMessage());
        }
        
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

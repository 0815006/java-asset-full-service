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
        
        // 1. 确定专区
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
        if ("product_zone".equals(zone)) {
            path.append("/").append(productId);
        }
        
        // 确保基础专区/产品目录存在
        java.io.File baseDir = new java.io.File(path.toString());
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        
        // 2. 拼接树形结构路径（不包含自身）
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
        String path = uploadDir + "/" + zone;
        if ("product_zone".equals(type)) {
            Long pid = productId != null ? productId : 0L;
            path += "/" + pid;
        }
        return Result.success(path.replace("//", "/"));
    }

    @PostMapping("/create-root-dir")
    public Result<Void> createRootDir(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        String pathStr = uploadDir + "/" + type;
        if ("product_zone".equals(type)) {
            Long productId = body.get("product_id") != null ? Long.valueOf(body.get("product_id").toString()) : 0L;
            pathStr += "/" + productId;
        }
        
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

    @PostMapping("/sync-extra")
    public Result<Void> syncExtra(@RequestBody Map<String, Object> body) {
        String type = (String) body.get("type");
        Long productId = 0L;
        if (body.get("product_id") != null && !body.get("product_id").toString().isEmpty()) {
            productId = Long.valueOf(body.get("product_id").toString());
        }

        // 1. 确定基础物理路径
        String basePhysicalPath = uploadDir + "/" + type;
        if ("product_zone".equals(type)) {
            basePhysicalPath += "/" + productId;
        }
        java.io.File baseDir = new java.io.File(basePhysicalPath);
        if (!baseDir.exists()) {
            return Result.error("物理根目录不存在");
        }

        // 2. 获取该单元的所有数据库记录
        List<AssetFile> dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getIsLatest, 1));

        Long startParentId = 0L;
        String parentTreePath = "/0/";

        if (productId == 0) {
            AssetFile zoneRoot = assetFileService.getOne(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getParentId, 0)
                    .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理"));
            if (zoneRoot != null) {
                startParentId = zoneRoot.getId();
                parentTreePath = zoneRoot.getTreePath();
                
                List<AssetFile> zoneFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .like(AssetFile::getTreePath, "/" + zoneRoot.getId() + "/")
                        .eq(AssetFile::getIsLatest, 1));
                dbFiles.clear();
                dbFiles.addAll(zoneFiles);
                dbFiles.add(zoneRoot);
            } else {
                return Result.error("未找到专区根节点，请先初始化数据");
            }
        }

        // 按 parentId 对数据库文件进行分组
        Map<Long, List<AssetFile>> dbMap = dbFiles.stream().collect(Collectors.groupingBy(AssetFile::getParentId));

        // 3. 递归同步
        syncRecursive(baseDir, startParentId, parentTreePath, productId, dbMap);

        return Result.success();
    }

    private void syncRecursive(java.io.File physicalDir, Long dbParentId, String parentTreePath, Long productId, Map<Long, List<AssetFile>> dbMap) {
        List<AssetFile> dbChildren = dbMap.getOrDefault(dbParentId, new ArrayList<>());
        java.io.File[] physicalChildren = physicalDir.exists() ? physicalDir.listFiles() : new java.io.File[0];
        if (physicalChildren == null) physicalChildren = new java.io.File[0];

        Set<String> dbFileNames = dbChildren.stream().map(AssetFile::getFileName).collect(Collectors.toSet());

        for (java.io.File pFile : physicalChildren) {
            String fileName = pFile.getName();
            boolean isDir = pFile.isDirectory();
            
            AssetFile currentDbFile = null;

            if (!dbFileNames.contains(fileName)) {
                // 数据库中不存在，需要入库
                AssetFile newFile = new AssetFile();
                newFile.setProductId(productId);
                newFile.setParentId(dbParentId);
                newFile.setFileName(fileName);
                newFile.setNodeType(isDir ? 1 : 2);
                newFile.setIsLatest(1);
                newFile.setVersionNo(1);
                newFile.setParseStatus(isDir ? 0 : 1);
                newFile.setCreatedBy(2L); // 默认陈东
                newFile.setCreatedAt(java.time.LocalDateTime.now());
                newFile.setUpdatedAt(java.time.LocalDateTime.now());
                
                if (!isDir) {
                    String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1) : "";
                    newFile.setExt(ext);
                    newFile.setFileSize(pFile.length());
                    newFile.setLocalPath(pFile.getAbsolutePath());
                }

                assetFileService.save(newFile);
                
                // 更新 treePath
                String currentTreePath = parentTreePath + newFile.getId() + "/";
                newFile.setTreePath(currentTreePath);
                assetFileService.updateById(newFile);
                
                if (!isDir) {
                    try {
                        searchService.index(newFile);
                    } catch (Exception e) {
                        System.err.println("同步到 Solr 失败: " + e.getMessage());
                    }
                }
                
                currentDbFile = newFile;
            } else {
                // 数据库中已存在，找到它
                currentDbFile = dbChildren.stream().filter(f -> f.getFileName().equals(fileName)).findFirst().orElse(null);
            }

            // 如果是目录，递归处理子目录
            if (isDir && currentDbFile != null) {
                syncRecursive(pFile, currentDbFile.getId(), currentDbFile.getTreePath(), productId, dbMap);
            }
        }
    }

    @GetMapping("/health-check")
    public Result<HealthCheckNode> healthCheck(@RequestParam("type") String type, @RequestParam(value = "product_id", required = false) Long productId) {
        String zone = type; // tech_zone, mgmt_zone, product_zone
        Long pid = productId != null ? productId : 0L;
        
        // 1. 确定基础物理路径
        String basePhysicalPath = uploadDir + "/" + zone;
        if ("product_zone".equals(type)) {
            basePhysicalPath += "/" + pid;
        }
        java.io.File baseDir = new java.io.File(basePhysicalPath);
        
        // 2. 获取该单元的所有数据库记录
        List<AssetFile> dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, pid)
                .eq(AssetFile::getIsLatest, 1));
        
        // 如果是公共专区，我们需要通过根节点进行过滤
        if (pid == 0) {
            AssetFile zoneRoot = assetFileService.getOne(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getParentId, 0)
                    .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理"));
            if (zoneRoot != null) {
                dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .like(AssetFile::getTreePath, "/" + zoneRoot.getId() + "/")
                        .eq(AssetFile::getIsLatest, 1));
                // 添加根节点自身
                dbFiles.add(zoneRoot);
            }
        }

        // 3. 构建健康检查树
        HealthCheckNode root = buildHealthTree(baseDir, dbFiles, pid, type);
        return Result.success(root);
    }

    private HealthCheckNode buildHealthTree(java.io.File dir, List<AssetFile> dbFiles, Long productId, String type) {
        HealthCheckNode node = new HealthCheckNode();
        node.setName(dir.getName());
        node.setNodeType(1);
        
        // 检查该目录是否存在于数据库中
        boolean existsInDb = dbFiles.stream().anyMatch(f -> f.getNodeType() == 1 && dir.getAbsolutePath().replace("\\", "/").endsWith(getRelativePathFromDb(f)));
        // 这部分比较复杂，因为 getPhysicalPath 使用的 fileName 可能不唯一。
        // 为了简化演示，我们使用基于名称的匹配来构建树形结构。
        
        // 使用更好的方法：从数据库树开始检查物理文件，然后添加额外的物理文件。
        return performFullCheck(dir, dbFiles, productId, type);
    }

    private HealthCheckNode performFullCheck(java.io.File physicalDir, List<AssetFile> dbFiles, Long productId, String type) {
        // 1. 找到该物理目录对应的数据库根节点
        // 对于第一次调用，physicalDir 是专区/产品根目录。
        HealthCheckNode root = new HealthCheckNode();
        root.setName(type.equals("product_zone") ? "产品专区-" + productId : (type.contains("tech") ? "测试技术与工艺专区" : "管理专区"));
        root.setNodeType(1);
        root.setStatus("normal");

        // 按 parentId 对数据库文件进行分组，方便遍历
        Map<Long, List<AssetFile>> dbMap = dbFiles.stream().collect(Collectors.groupingBy(AssetFile::getParentId));
        
        // 找到起始的 parentId
        Long startParentId = 0L;
        if (productId == 0) {
             AssetFile zoneRoot = dbFiles.stream().filter(f -> f.getParentId() == 0).findFirst().orElse(null);
             if (zoneRoot != null) {
                 // 我们从专区根节点的子节点开始
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

        // 1. 将数据库记录与物理文件进行比对
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

        // 2. 检查不在数据库中的物理文件 (多余文件)
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
        // 辅助方法，用于获取相对路径进行匹配
        return ""; // 暂时简化
    }

    @GetMapping("/tree")
    public Result<List<AssetFile>> getTree(@RequestParam("product_id") Long productId, @RequestParam("parent_id") Long parentId) {
        List<AssetFile> list = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getParentId, parentId)
                .eq(AssetFile::getIsLatest, 1)
                .orderByAsc(AssetFile::getNodeType) // 文件夹排在前面
                .orderByAsc(AssetFile::getId));

        list.forEach(node -> {
            // 检查是否有子节点（用于懒加载）
            if (node.getNodeType() == 1) {
                long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getParentId, node.getId())
                        .eq(AssetFile::getIsLatest, 1));
                node.setHasChildren(count > 0);

                long subFolderCount = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getParentId, node.getId())
                        .eq(AssetFile::getNodeType, 1) // 仅文件夹
                        .eq(AssetFile::getIsLatest, 1));
                node.setSubFolderFlag(subFolderCount > 0 ? 1 : 0);
            } else {
                node.setHasChildren(false);
                node.setSubFolderFlag(0);
            }
            
            // 模拟权限
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

        // 检查是否存在同名文件夹
        long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getParentId, parentId)
                .eq(AssetFile::getFileName, folderName)
                .eq(AssetFile::getNodeType, 1) // 文件夹
                .eq(AssetFile::getIsLatest, 1));
        
        if (count > 0) {
            return Result.error("当前目录下已存在同名目录");
        }

        AssetFile folder = new AssetFile();
        folder.setProductId(productId);
        folder.setParentId(parentId);
        folder.setFileName(folderName);
        folder.setNodeType(1); // 文件夹
        folder.setIsLatest(1);
        folder.setVersionNo(1);
        folder.setParseStatus(0); // 无需解析
        folder.setCreatedBy(2L); // 陈东
        folder.setCreatedAt(java.time.LocalDateTime.now());
        folder.setUpdatedAt(java.time.LocalDateTime.now());
        
        assetFileService.save(folder);
        
        // 更新树形路径
        String parentPath = "/0/";
        if (parentId != 0) {
            AssetFile parent = assetFileService.getById(parentId);
            if (parent != null) {
                parentPath = parent.getTreePath();
            }
        }
        folder.setTreePath(parentPath + folder.getId() + "/");
        assetFileService.updateById(folder);

        // 创建物理目录并进行严格检查
        String parentPhysicalPath = getPhysicalPath(folder);
        java.io.File parentDir = new java.io.File(parentPhysicalPath);
        if (!parentDir.exists()) {
            // 如果父目录在存储中不存在，根据用户要求报错
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
        if (file == null) return Result.error("文件不存在");

        String oldPath = file.getTreePath();
        
        // 计算新的路径前缀
        String newParentPath = "/0/";
        if (targetParentId != 0) {
            AssetFile parent = assetFileService.getById(targetParentId);
            if (parent != null) {
                newParentPath = parent.getTreePath();
            }
        }
        
        String newPath = newParentPath + file.getId() + "/";
        
        // 更新当前节点
        file.setParentId(targetParentId);
        file.setTreePath(newPath);
        assetFileService.updateById(file);
        
        // 递归更新子节点
        if (file.getNodeType() == 1) { // 文件夹
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
        // 模拟上传逻辑
        String fileName = file.getOriginalFilename();
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        
        // 检查是否存在同名文件以进行版本控制
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
        newFile.setNodeType(2); // 文件
        newFile.setParseStatus(1); // 排队中
        newFile.setCreatedBy(2L); // 陈东
        newFile.setCreatedAt(java.time.LocalDateTime.now());
        newFile.setUpdatedAt(java.time.LocalDateTime.now());
        
        assetFileService.save(newFile);
        
        // 更新树形路径
        String parentPath = "/0/";
        if (parentId != 0) {
            AssetFile parent = assetFileService.getById(parentId);
            if (parent != null) {
                parentPath = parent.getTreePath();
            }
        }
        newFile.setTreePath(parentPath + newFile.getId() + "/");
        assetFileService.updateById(newFile);
        
        // 物理保存并进行严格检查
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
        
        // 索引到 Solr
        searchService.index(newFile);
        
        return Result.success(newFile);
    }
    
    @GetMapping("/{id}/detail")
    public Result<AssetFile> detail(@PathVariable Long id) {
        AssetFile file = assetFileService.getById(id);
        if (file == null) return Result.error("文件不存在");
        
        // 模拟 MD 文件内容
        if ("md".equalsIgnoreCase(file.getExt())) {
            // 在实际应用中，从文件系统读取
        }
        
        return Result.success(file);
    }

    @PostMapping("/download")
    public void download(@RequestBody Map<String, List<Long>> body, HttpServletResponse response) {
        List<Long> ids = body.get("file_ids");
        if (ids == null || ids.isEmpty()) return;
        
        // 红线 8：限制检查
        if (ids.size() > 50) {
            response.setStatus(400);
            try {
                response.getWriter().write("{\"code\": 400, \"message\": \"文件数量过多 (最多 50 个)\"}");
            } catch (IOException e) {}
            return;
        }
        
        List<AssetFile> files = assetFileService.listByIds(ids);
        long totalSize = files.stream().mapToLong(AssetFile::getFileSize).sum();
        if (totalSize > 500 * 1024 * 1024) { // 500MB
             response.setStatus(400);
             try {
                response.getWriter().write("{\"code\": 400, \"message\": \"总大小超过 500MB\"}");
            } catch (IOException e) {}
            return;
        }
        
        // 模拟下载
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"download.zip\"");
        try {
            response.getWriter().write("模拟文件内容");
        } catch (IOException e) {}
    }
}

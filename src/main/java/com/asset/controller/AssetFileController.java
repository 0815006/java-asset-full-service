package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.service.AssetFileService;
import com.asset.service.SearchService;
import com.asset.entity.UserFileState;
import com.asset.service.UserFileStateService;
import com.asset.entity.AssetAccessLog;
import com.asset.service.AssetAccessLogService;
import com.asset.entity.UserFileStar;
import com.asset.service.UserFileStarService;
import com.asset.entity.AssetCurated;
import com.asset.service.AssetCuratedService;
import com.asset.service.IAssetCuratedService;
import com.asset.service.IProductUseRankingService;
import com.asset.dto.ProductUseRankingDTO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Comparator;
import lombok.extern.slf4j.Slf4j;

/**
 * 资产文件管理控制器
 * 处理文件的上传、下载、预览、树形结构、同步及健康检查等核心业务
 */
@Slf4j
@RestController
@RequestMapping("/api/assets")
@CrossOrigin
public class AssetFileController {

    @Autowired
    private AssetFileService assetFileService;

    @Autowired
    private com.asset.mapper.AssetFileMapper assetFileMapper;
    
    @Autowired
    private SearchService searchService;

    @Autowired
    private UserFileStateService userFileStateService;

    @Autowired
    private AssetAccessLogService assetAccessLogService;

    @Autowired
    private UserFileStarService userFileStarService;

    @Autowired
    private IAssetCuratedService assetCuratedService;

    @Autowired
    private IProductUseRankingService productUseRankingService;

    @Autowired
    private com.asset.service.ProductService productService;

    private static final Set<String> syncingKeys = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, SyncProgress> syncProgressMap = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, BatchProgress> batchProgressMap = new java.util.concurrent.ConcurrentHashMap<>();

    public static class SyncProgress {
        public int totalFiles = 0;
        public int processedFiles = 0;
        public String currentFileName = "";
        public boolean isFinished = false;
        public String errorMsg = null;
    }

    public static class BatchProgress {
        public int total = 0;
        public int current = 0;
        public String status = "running"; // running, finished, error
        public String message = "";
    }

    @org.springframework.beans.factory.annotation.Value("${file.upload-dir}")
    private String uploadDir;

    @org.springframework.beans.factory.annotation.Value("${file.recycle-bin-dir}")
    private String recycleBinDir;

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
            boolean isFirstNode = true; // 用于识别并跳过专区根节点
            for (String idStr : ids) {
                if (idStr.isEmpty() || idStr.equals("0")) continue;
                
                // 如果是技术或管理专区，跳过 treePath 中的第一个有效节点（即数据库中的虚拟专区根节点）
                // 这样物理路径就不会包含“/测试技术与工艺专区”或“/管理专区”这一层
                if (!"product_zone".equals(zone) && isFirstNode) {
                    isFirstNode = false;
                    continue;
                }
                isFirstNode = false;

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

    /**
     * 获取指定专区/产品的存储物理路径
     */
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

    /**
     * 创建存储根目录
     */
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

    private int countFiles(java.io.File dir) {
        int count = 0;
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                count++;
                if (f.isDirectory()) {
                    count += countFiles(f);
                }
            }
        }
        return count;
    }

    /**
     * 一键入库：将物理存储中存在但数据库中缺失的文件同步到数据库
     */
    @PostMapping("/sync-extra")
    public Result<Void> syncExtra(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String type = (String) body.get("type");
        Long productId = 0L;
        if (body.get("product_id") != null && !body.get("product_id").toString().isEmpty()) {
            productId = Long.valueOf(body.get("product_id").toString());
        }

        String syncKey = type + ":" + productId;
        if (!syncingKeys.add(syncKey)) {
            return Result.error("该专区/产品正在入库中，请勿重复点击");
        }

        // 初始化进度
        SyncProgress progress = new SyncProgress();
        syncProgressMap.put(syncKey, progress);

        // 1. 确定基础物理路径
        String basePhysicalPath = uploadDir + "/" + type;
        if ("product_zone".equals(type)) {
            basePhysicalPath += "/" + productId;
        }
        java.io.File baseDir = new java.io.File(basePhysicalPath);
        if (!baseDir.exists()) {
            syncingKeys.remove(syncKey);
            return Result.error("物理根目录不存在");
        }

        // 预先计算总文件数
        progress.totalFiles = countFiles(baseDir);

        // 异步执行入库
        Long finalProductId = productId;
        Long finalUserId = userId != null ? userId : 2L; // 默认陈东作为兜底
        new Thread(() -> {
            try {
                // 2. 获取该单元的所有数据库记录
                List<AssetFile> dbFiles = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getProductId, finalProductId)
                        .eq(AssetFile::getIsLatest, 1));

                Long startParentId = 0L;
                String parentTreePath = "/0/";

                if (finalProductId == 0) {
                    List<AssetFile> zoneRoots = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                            .eq(AssetFile::getParentId, 0)
                            .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理")
                            .last("limit 1"));
                    AssetFile zoneRoot = zoneRoots.isEmpty() ? null : zoneRoots.get(0);
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
                        progress.errorMsg = "未找到专区根节点，请先初始化数据";
                        progress.isFinished = true;
                        return;
                    }
                }

                // 按 parentId 对数据库文件进行分组
                Map<Long, List<AssetFile>> dbMap = dbFiles.stream().collect(Collectors.groupingBy(AssetFile::getParentId));

                // 3. 递归同步
                syncRecursive(baseDir, startParentId, parentTreePath, finalProductId, dbMap, progress, finalUserId);

                progress.isFinished = true;
            } catch (Exception e) {
                e.printStackTrace();
                progress.errorMsg = "入库发生异常: " + e.getMessage();
                progress.isFinished = true;
            } finally {
                syncingKeys.remove(syncKey);
            }
        }).start();

        return Result.success();
    }

    /**
     * 获取入库进度
     */
    @GetMapping("/sync-progress")
    public Result<SyncProgress> getSyncProgress(@RequestParam("type") String type, @RequestParam(value = "product_id", required = false) Long productId) {
        Long pid = productId != null ? productId : 0L;
        String syncKey = type + ":" + pid;
        SyncProgress progress = syncProgressMap.get(syncKey);
        if (progress == null) {
            // 如果没有进度，说明可能已经完成或者还没开始
            SyncProgress empty = new SyncProgress();
            empty.isFinished = true;
            return Result.success(empty);
        }
        return Result.success(progress);
    }

    private void syncRecursive(java.io.File physicalDir, Long dbParentId, String parentTreePath, Long productId, Map<Long, List<AssetFile>> dbMap, SyncProgress progress, Long userId) {
        List<AssetFile> dbChildren = dbMap.getOrDefault(dbParentId, new ArrayList<>());
        java.io.File[] physicalChildren = physicalDir.exists() ? physicalDir.listFiles() : new java.io.File[0];
        if (physicalChildren == null) physicalChildren = new java.io.File[0];

        Set<String> dbFileNames = dbChildren.stream().map(AssetFile::getFileName).collect(Collectors.toSet());

        for (java.io.File pFile : physicalChildren) {
            String fileName = pFile.getName();
            boolean isDir = pFile.isDirectory();
            
            AssetFile currentDbFile = null;

            if (!dbFileNames.contains(fileName)) {
                // 再次检查数据库，防止并发导致的重复入库
                List<AssetFile> existings = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getProductId, productId)
                        .eq(AssetFile::getParentId, dbParentId)
                        .eq(AssetFile::getFileName, fileName)
                        .eq(AssetFile::getIsLatest, 1)
                        .last("limit 1"));
                AssetFile existing = existings.isEmpty() ? null : existings.get(0);
                
                if (existing != null) {
                    currentDbFile = existing;
                } else {
                    // 数据库中不存在，需要入库
                    AssetFile newFile = new AssetFile();
                    newFile.setProductId(productId);
                    newFile.setParentId(dbParentId);
                    newFile.setFileName(fileName);
                    newFile.setNodeType(isDir ? 1 : 2);
                    newFile.setIsLatest(1);
                    newFile.setVersionNo(1);
                    newFile.setParseStatus(isDir ? 0 : 1);
                    newFile.setCreatedBy(userId); 
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
                    
                    // 更新产品资产总数
                    if (productId != null && productId > 0) {
                        productService.updateAssetCount(productId, 1);
                    }
                    
                    if (!isDir) {
                        try {
                            searchService.index(newFile);
                        } catch (Exception e) {
                            System.err.println("同步到 Solr 失败: " + e.getMessage());
                        }
                    }
                    
                    currentDbFile = newFile;
                }
            } else {
                // 数据库中已存在，找到它
                currentDbFile = dbChildren.stream().filter(f -> f.getFileName().equals(fileName)).findFirst().orElse(null);
            }

            // 如果是目录，递归处理子目录
            if (isDir && currentDbFile != null) {
                syncRecursive(pFile, currentDbFile.getId(), currentDbFile.getTreePath(), productId, dbMap, progress, userId);
            }
            
            progress.processedFiles++;
            progress.currentFileName = fileName;
        }
    }

    /**
     * 存储健康检查：比对数据库记录与物理存储的一致性
     */
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
            List<AssetFile> zoneRoots = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getParentId, 0)
                    .like(AssetFile::getFileName, type.contains("tech") ? "测试技术" : "管理")
                    .last("limit 1"));
            AssetFile zoneRoot = zoneRoots.isEmpty() ? null : zoneRoots.get(0);
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

    /**
     * 获取资产树结构（支持懒加载）
     */
    @GetMapping("/tree")
    public Result<List<AssetFile>> getTree(@RequestParam("product_id") Long productId, 
                                           @RequestParam("parent_id") Long parentId, 
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        List<AssetFile> list = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getProductId, productId)
                .eq(AssetFile::getParentId, parentId)
                .eq(AssetFile::getIsLatest, 1)
                .orderByAsc(AssetFile::getNodeType) // 文件夹排在前面
                .orderByAsc(AssetFile::getId));

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        list.forEach(node -> {
            // 检查是否有子节点（用于懒加载）
            if (node.getNodeType() == 1) {
                long count = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getProductId, productId)
                        .eq(AssetFile::getParentId, node.getId())
                        .eq(AssetFile::getIsLatest, 1));
                node.setHasChildren(count > 0);

                long subFolderCount = assetFileService.count(new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getProductId, productId)
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

            // 设置 isNew 字段
            node.setIsNew(isNewFile(node, currentUserId));
            // 设置 isStarred 字段
            node.setIsStarred(isStarredFile(node, currentUserId));
        });

        return Result.success(list);
    }

    /**
     * 创建新文件夹
     */
    @PostMapping("/folder")
    public Result<AssetFile> createFolder(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
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
        folder.setCreatedBy(userId != null ? userId : 2L); 
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

    /**
     * 重命名资产
     */
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
    
    /**
     * 删除资产（逻辑删除并移动物理文件到回收站）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        AssetFile file = assetFileService.getById(id);
        if (file == null) return Result.error("文件不存在");

        // 1. 物理文件移动到回收站
        if (file.getNodeType() == 2 && file.getLocalPath() != null && !file.getLocalPath().isEmpty()) {
            try {
                java.io.File sourceFile = new java.io.File(file.getLocalPath());
                if (sourceFile.exists()) {
                    java.io.File recycleDir = new java.io.File(recycleBinDir);
                    if (!recycleDir.exists()) recycleDir.mkdirs();

                    // 新文件名：ID_原文件名
                    String recycleFileName = file.getId() + "_" + file.getFileName();
                    java.io.File destFile = new java.io.File(recycleDir, recycleFileName);
                    
                    Files.move(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    
                    // 更新数据库中的物理路径为回收站路径
                    file.setLocalPath(destFile.getAbsolutePath());
                    assetFileService.updateById(file);
                }
            } catch (IOException e) {
                log.error("Failed to move file to recycle bin: {}", e.getMessage());
                return Result.error("文件移动至回收站失败: " + e.getMessage());
            }
        }

        // 2. 数据库软删除 (MyBatis Plus 自动处理 is_deleted)
        assetFileService.removeById(id);
        
        // 更新产品资产总数
        if (file.getProductId() != null && file.getProductId() > 0) {
            productService.updateAssetCount(file.getProductId(), -1);
        }
        
        // 3. 同步删除 Solr 索引
        searchService.delete(id);
        
        // 4. 清理核心资产标记
        assetCuratedService.remove(new LambdaQueryWrapper<AssetCurated>().eq(AssetCurated::getFileId, id));
        
        // 5. 清理用户收藏记录
        userFileStarService.remove(new LambdaQueryWrapper<UserFileStar>().eq(UserFileStar::getFileId, id));
        
        return Result.success();
    }

    /**
     * 获取回收站列表
     */
    @GetMapping("/recycle-bin")
    public Result<List<Map<String, Object>>> getRecycleBin() {
        // 使用自定义 Mapper 方法绕过逻辑删除过滤
        List<AssetFile> list = assetFileMapper.selectDeletedFiles();
        
        List<Map<String, Object>> result = list.stream().map(file -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", file.getId());
            map.put("fileName", file.getFileName());
            map.put("updatedAt", file.getUpdatedAt());
            map.put("nodeType", file.getNodeType());
            
            // 计算原物理路径（用于展示）
            String path = getPhysicalPath(file);
            map.put("originalPath", path.replace(uploadDir, "")); 
            
            return map;
        }).collect(Collectors.toList());
        
        return Result.success(result);
    }

    /**
     * 恢复已删除的资产
     */
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        // 使用自定义 Mapper 方法绕过逻辑删除过滤
        AssetFile file = assetFileMapper.selectByIdWithDeleted(id);
        if (file == null) return Result.error("文件不存在");
        if (file.getIsDeleted() == 0) return Result.error("文件未被删除，无需恢复");

        // 1. 校验原目录是否存在
        String originalDirStr = getPhysicalPath(file);
        java.io.File originalDir = new java.io.File(originalDirStr);
        if (!originalDir.exists()) {
            return Result.error("原存储目录已不存在，无法恢复，请联系运维人员。目录：" + originalDirStr);
        }

        // 2. 校验同名冲突
        java.io.File targetFile = new java.io.File(originalDir, file.getFileName());
        if (targetFile.exists()) {
            return Result.error("恢复失败：原目录下已存在同名文件 \"" + file.getFileName() + "\"，请先处理冲突。");
        }

        // 3. 物理文件移回
        if (file.getLocalPath() != null && !file.getLocalPath().isEmpty()) {
            try {
                java.io.File sourceFile = new java.io.File(file.getLocalPath());
                if (sourceFile.exists()) {
                    Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    file.setLocalPath(targetFile.getAbsolutePath());
                } else {
                    return Result.error("回收站中未找到物理文件，无法恢复。");
                }
            } catch (IOException e) {
                return Result.error("文件移回失败: " + e.getMessage());
            }
        }

        // 4. 数据库状态恢复
        assetFileMapper.restoreById(id, targetFile.getAbsolutePath());
        
        // 更新产品资产总数
        if (file.getProductId() != null && file.getProductId() > 0) {
            productService.updateAssetCount(file.getProductId(), 1);
        }
        
        // 5. 重新同步 Solr
        file.setIsDeleted(0);
        file.setLocalPath(targetFile.getAbsolutePath());
        if (file.getNodeType() == 2) {
            searchService.index(file);
        }

        return Result.success();
    }

    /**
     * 彻底删除资产（物理删除）
     */
    @DeleteMapping("/{id}/permanent")
    public Result<Void> permanentDelete(@PathVariable Long id) {
        // 使用自定义 Mapper 方法绕过逻辑删除过滤
        AssetFile file = assetFileMapper.selectByIdWithDeleted(id);
        if (file == null) return Result.error("文件不存在");

        // 1. 物理删除回收站文件
        if (file.getLocalPath() != null && !file.getLocalPath().isEmpty()) {
            java.io.File physicalFile = new java.io.File(file.getLocalPath());
            if (physicalFile.exists() && physicalFile.getAbsolutePath().contains("recycle_bin")) {
                physicalFile.delete();
            }
        }

        // 2. 数据库物理删除
        assetFileMapper.deleteByIdPhysically(id);

        return Result.success();
    }

    /**
     * 批量恢复回收站所有资产
     */
    @PostMapping("/restore-all")
    public Result<String> restoreAll() {
        List<AssetFile> deletedFiles = assetFileMapper.selectDeletedFiles();
        if (deletedFiles.isEmpty()) {
            return Result.error("回收站为空");
        }

        String taskId = UUID.randomUUID().toString();
        BatchProgress progress = new BatchProgress();
        progress.total = deletedFiles.size();
        batchProgressMap.put(taskId, progress);

        new Thread(() -> {
            try {
                for (AssetFile file : deletedFiles) {
                    try {
                        restore(file.getId());
                    } catch (Exception e) {
                        log.error("Batch restore failed for file {}: {}", file.getId(), e.getMessage());
                    }
                    progress.current++;
                }
                progress.status = "finished";
            } catch (Exception e) {
                progress.status = "error";
                progress.message = e.getMessage();
            }
        }).start();

        return Result.success(taskId);
    }

    /**
     * 批量彻底删除回收站所有资产
     */
    @DeleteMapping("/permanent-all")
    public Result<String> permanentDeleteAll() {
        List<AssetFile> deletedFiles = assetFileMapper.selectDeletedFiles();
        if (deletedFiles.isEmpty()) {
            return Result.error("回收站为空");
        }

        String taskId = UUID.randomUUID().toString();
        BatchProgress progress = new BatchProgress();
        progress.total = deletedFiles.size();
        batchProgressMap.put(taskId, progress);

        new Thread(() -> {
            try {
                for (AssetFile file : deletedFiles) {
                    try {
                        permanentDelete(file.getId());
                    } catch (Exception e) {
                        log.error("Batch permanent delete failed for file {}: {}", file.getId(), e.getMessage());
                    }
                    progress.current++;
                }
                progress.status = "finished";
            } catch (Exception e) {
                progress.status = "error";
                progress.message = e.getMessage();
            }
        }).start();

        return Result.success(taskId);
    }

    /**
     * 获取批量操作进度
     */
    @GetMapping("/batch-progress/{taskId}")
    public Result<BatchProgress> getBatchProgress(@PathVariable String taskId) {
        BatchProgress progress = batchProgressMap.get(taskId);
        if (progress == null) {
            return Result.error("任务不存在");
        }
        return Result.success(progress);
    }

    /**
     * 移动资产到新目录
     */
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

    /**
     * 上传新文件
     */
    @PostMapping("/upload")
    public Result<AssetFile> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("product_id") Long productId,
                                    @RequestParam("parent_id") Long parentId,
                                    @RequestParam(required = false) String zoneType,
                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 模拟上传逻辑
        String fileName = file.getOriginalFilename();
        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        
        // 检查是否存在同名文件以进行版本控制
        List<AssetFile> existings = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
            .eq(AssetFile::getProductId, productId)
            .eq(AssetFile::getParentId, parentId)
            .eq(AssetFile::getFileName, fileName)
            .eq(AssetFile::getIsLatest, 1)
            .last("limit 1"));
        AssetFile existing = existings.isEmpty() ? null : existings.get(0);
            
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
        newFile.setCreatedBy(userId != null ? userId : 2L); 
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
                dir.mkdirs();
            }
            java.io.File dest = new java.io.File(dir, fileName);
            file.transferTo(dest);
            
            newFile.setLocalPath(dest.getAbsolutePath());
            assetFileService.updateById(newFile);

            // 更新产品资产总数
            if (productId != null && productId > 0) {
                productService.updateAssetCount(productId, 1);
            }
        } catch (java.io.IOException e) {
            return Result.error("文件保存失败: " + e.getMessage());
        }
        
        // 索引到 Solr
        searchService.index(newFile);
        
        return Result.success(newFile);
    }
    
    /**
     * 更新现有文件（版本升级）
     */
    @PostMapping("/{id}/update")
    public Result<AssetFile> updateFile(@PathVariable Long id, 
                                        @RequestParam("file") MultipartFile file,
                                        @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AssetFile oldFile = assetFileService.getById(id);
        if (oldFile == null) return Result.error("原文件不存在");
        
        // 1. 标记旧版本为非最新
        oldFile.setIsLatest(0);
        assetFileService.updateById(oldFile);
        
        // 2. 创建新版本记录
        AssetFile newFile = new AssetFile();
        newFile.setProductId(oldFile.getProductId());
        newFile.setParentId(oldFile.getParentId());
        newFile.setTreePath(oldFile.getTreePath());
        newFile.setFileName(oldFile.getFileName());
        newFile.setExt(oldFile.getExt());
        newFile.setLocalPath(oldFile.getLocalPath());
        newFile.setFileSize(file.getSize());
        newFile.setVersionNo(oldFile.getVersionNo() + 1);
        newFile.setIsLatest(1);
        newFile.setNodeType(2);
        newFile.setParseStatus(1);
        newFile.setCreatedBy(userId != null ? userId : 2L); 
        newFile.setCreatedAt(java.time.LocalDateTime.now());
        newFile.setUpdatedAt(java.time.LocalDateTime.now());
        
        assetFileService.save(newFile);
        
        // 3. 物理覆盖原文件
        try {
            java.io.File dest = new java.io.File(oldFile.getLocalPath());
            file.transferTo(dest);
        } catch (java.io.IOException e) {
            return Result.error("物理文件覆盖失败: " + e.getMessage());
        }
        
        // 4. 同步 Solr
        searchService.index(newFile);
        
        return Result.success(newFile);
    }

    /**
     * 获取资产详情
     */
    @GetMapping("/{id}/details")
    public Result<AssetFile> getAssetDetails(@PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AssetFile file = assetFileService.getById(id);
        if (file == null) {
            return Result.error("File not found");
        }

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L; 
        file.setIsNew(isNewFile(file, currentUserId));
        file.setIsStarred(isStarredFile(file, currentUserId));
        // 为了兼容前端逻辑，同时设置 currentUserStarred
        file.setCurrentUserStarred(isStarredFile(file, currentUserId));

        return Result.success(file);
    }

    private boolean isStarredFile(AssetFile file, Long userId) {
        LambdaQueryWrapper<UserFileStar> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFileStar::getUserId, userId);
        wrapper.eq(UserFileStar::getFileId, file.getId());
        return userFileStarService.count(wrapper) > 0;
    }

    private boolean isNewFile(AssetFile file, Long userId) {
        // 14 天是金线：不仅仅是 New 标，全行“最新更新榜”也应只展示 14 天内的内容。
        // 条件1：必须是 14 天内更新的文件
        if (file.getUpdatedAt().isBefore(java.time.LocalDateTime.now().minusDays(14))) {
            return false;
        }

        // 条件2：用户未读过 OR 文件更新时间晚于最后阅读时间
        LambdaQueryWrapper<UserFileState> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFileState::getUserId, userId);
        wrapper.eq(UserFileState::getFileId, file.getId());
        UserFileState userFileState = userFileStateService.getOne(wrapper);

        if (userFileState == null || file.getUpdatedAt().isAfter(userFileState.getLastReadAt())) {
            return true;
        }
        return false;
    }

    /**
     * 获取资产详情（别名接口）
     */
    @GetMapping("/{id}/detail")
    public Result<AssetFile> detail(@PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        AssetFile file = assetFileService.getById(id);
        if (file == null) return Result.error("文件不存在");

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;
        file.setIsNew(isNewFile(file, currentUserId));
        file.setIsStarred(isStarredFile(file, currentUserId));
        file.setCurrentUserStarred(isStarredFile(file, currentUserId));
        
        return Result.success(file);
    }

    /**
     * 预览或下载文件内容
     * 使用 ResponseEntity<Resource> 以支持 HTTP Range 请求（分段加载），解决大文件及 PDF 预览问题
     */
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewFile(@PathVariable Long id, 
                         @RequestParam(value = "download", required = false, defaultValue = "false") boolean download,
                         @RequestParam(value = "userId", required = false) Long userId,
                         @RequestHeader(value = "X-User-Id", required = false) Long headerUserId,
                         HttpServletRequest request) {
        log.info("收到预览/下载请求，ID={}, download={}, method={}", id, download, request.getMethod());
        AssetFile file = assetFileService.getById(id);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (file.getLocalPath() == null || file.getLocalPath().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        java.io.File physicalFile = new java.io.File(file.getLocalPath());
        if (!physicalFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        
        String contentType = "application/octet-stream";
        String ext = file.getExt();
        // 增强后缀名识别逻辑，处理 ext 为空的情况
        if ((ext == null || ext.isEmpty()) && file.getFileName().contains(".")) {
            ext = file.getFileName().substring(file.getFileName().lastIndexOf(".") + 1);
        }
        
        if ("pdf".equalsIgnoreCase(ext)) {
            contentType = "application/pdf";
        } else if ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext)) {
            contentType = "image/jpeg";
        } else if ("png".equalsIgnoreCase(ext)) {
            contentType = "image/png";
        } else if ("gif".equalsIgnoreCase(ext)) {
            contentType = "image/gif";
        } else if ("txt".equalsIgnoreCase(ext) || "md".equalsIgnoreCase(ext) || "sql".equalsIgnoreCase(ext) || "log".equalsIgnoreCase(ext) || "sh".equalsIgnoreCase(ext) || "py".equalsIgnoreCase(ext) ||
                "json".equalsIgnoreCase(ext) || "xml".equalsIgnoreCase(ext) || "java".equalsIgnoreCase(ext) || "js".equalsIgnoreCase(ext) || "css".equalsIgnoreCase(ext) ||
                "yml".equalsIgnoreCase(ext) || "yaml".equalsIgnoreCase(ext) || "properties".equalsIgnoreCase(ext) || "ini".equalsIgnoreCase(ext) || "conf".equalsIgnoreCase(ext) || "env".equalsIgnoreCase(ext) ||
                "bat".equalsIgnoreCase(ext) || "cmd".equalsIgnoreCase(ext) || "ts".equalsIgnoreCase(ext) || "vue".equalsIgnoreCase(ext) || "html".equalsIgnoreCase(ext) ||
                "c".equalsIgnoreCase(ext) || "cpp".equalsIgnoreCase(ext) || "h".equalsIgnoreCase(ext) || "go".equalsIgnoreCase(ext) || "php".equalsIgnoreCase(ext) || "csv".equalsIgnoreCase(ext)) {
            contentType = "text/plain;charset=UTF-8";
        }

        // 记录访问日志
        AssetAccessLog accessLog = new AssetAccessLog();
        Long currentUserId = headerUserId != null ? headerUserId : (userId != null ? userId : 2L);
        accessLog.setUserId(currentUserId);
        accessLog.setFileId(file.getId());
        accessLog.setProductId(file.getProductId());
        accessLog.setCreatedAt(java.time.LocalDateTime.now());
        assetAccessLogService.save(accessLog);

        Resource resource = new FileSystemResource(physicalFile);
        
        String contentDisposition = "inline";
        try {
            String encodedFileName = java.net.URLEncoder.encode(file.getFileName(), "UTF-8").replaceAll("\\+", "%20");
            if (download) {
                contentDisposition = "attachment; filename=\"" + encodedFileName + "\"";
            } else {
                // 预览模式下，对于 PDF 建议只返回 inline，不带 filename，以减少浏览器强制下载的概率
                if ("pdf".equalsIgnoreCase(ext)) {
                    contentDisposition = "inline";
                } else {
                    contentDisposition = "inline; filename=\"" + encodedFileName + "\"";
                }
            }
        } catch (java.io.UnsupportedEncodingException e) {
            contentDisposition = download ? "attachment; filename=\"file\"" : "inline";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes") // 显式声明支持 Range 请求
                .body(resource);
    }

    /**
     * 获取最近访问的文件列表
     */
    @GetMapping("/recent-access")
    public Result<List<Map<String, Object>>> getRecentAccessedFiles(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 1. 查询最近访问日志，按访问时间倒序
        // 使用 MyBatis-Plus 的自定义 SQL 或聚合查询来去重
        // 这里为了简单，先查询较多记录，然后在内存中去重并分页
        LambdaQueryWrapper<AssetAccessLog> logWrapper = new LambdaQueryWrapper<>();
        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;
        logWrapper.eq(AssetAccessLog::getUserId, currentUserId);
        logWrapper.orderByDesc(AssetAccessLog::getCreatedAt);
        // 查出较多数据以保证去重后仍有足够数据
        logWrapper.last("LIMIT 200"); 

        List<AssetAccessLog> accessLogs = assetAccessLogService.list(logWrapper);

        if (accessLogs.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 2. 内存去重：按 fileId 分组，取每组最新的记录，并限制为 50 条
        List<AssetAccessLog> distinctLogs = accessLogs.stream()
                .collect(Collectors.toMap(
                        AssetAccessLog::getFileId,
                        log -> log,
                        (existing, replacement) -> existing // 保留时间最晚的（因为原始列表已按时间倒序）
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(AssetAccessLog::getCreatedAt).reversed())
                .limit(50)
                .collect(Collectors.toList());

        // 3. 获取文件ID列表
        List<Long> fileIds = distinctLogs.stream()
                                       .map(AssetAccessLog::getFileId)
                                       .collect(Collectors.toList());

        // 4. 根据文件ID查询 AssetFile 详情
        List<AssetFile> files = assetFileService.listByIds(fileIds);
        Map<Long, AssetFile> fileMap = files.stream()
                                            .collect(Collectors.toMap(AssetFile::getId, f -> f));

        // 5. 组装结果
        List<Map<String, Object>> result = distinctLogs.stream()
                .map(log -> {
                    AssetFile file = fileMap.get(log.getFileId());
                    if (file == null) return null;
                    
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", file.getId());
                    map.put("fileName", file.getFileName());
                    map.put("ext", file.getExt());
                    map.put("nodeType", file.getNodeType());
                    map.put("productId", file.getProductId());
                    map.put("treePath", file.getTreePath());
                    map.put("isNew", isNewFile(file, currentUserId));
                    map.put("isStarred", isStarredFile(file, currentUserId));
                    // 查找该文件的收藏记录以获取置顶状态
                    UserFileStar star = userFileStarService.getOne(new LambdaQueryWrapper<UserFileStar>()
                            .eq(UserFileStar::getUserId, currentUserId)
                            .eq(UserFileStar::getFileId, file.getId()));
                    map.put("isPinned", star != null && star.getIsPinned());
                    map.put("accessTime", log.getCreatedAt());
                    return map;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return Result.success(result);
    }

    /**
     * OnlyOffice 回调接口
     */
    @PostMapping("/callback")
    public void callback(@RequestBody Map<String, Object> body) {
        // OnlyOffice 回调，目前仅记录日志
        System.out.println("收到 OnlyOffice 回调: " + body);
    }

    /**
     * 记录用户阅读状态（消除 New 标）
     */
    @PostMapping("/record-read-state")
    public Result<Void> recordReadState(@RequestBody Map<String, Long> body) {
        Long fileId = body.get("file_id");
        Long userId = body.get("user_id"); // 实际应从认证上下文获取

        if (fileId == null || userId == null) {
            return Result.error("文件ID和用户ID不能为空");
        }

        try {
            LambdaQueryWrapper<UserFileState> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserFileState::getUserId, userId);
            wrapper.eq(UserFileState::getFileId, fileId);
            UserFileState userFileState = userFileStateService.getOne(wrapper);

            if (userFileState == null) {
                userFileState = new UserFileState();
                userFileState.setUserId(userId);
                userFileState.setFileId(fileId);
                userFileState.setLastReadAt(java.time.LocalDateTime.now());
                userFileState.setUpdatedAt(java.time.LocalDateTime.now());
                try {
                    userFileStateService.save(userFileState);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    // 如果并发导致插入失败，说明记录已存在，执行更新逻辑
                    UserFileState existing = userFileStateService.getOne(wrapper);
                    if (existing != null) {
                        existing.setLastReadAt(java.time.LocalDateTime.now());
                        existing.setUpdatedAt(java.time.LocalDateTime.now());
                        userFileStateService.updateById(existing);
                    }
                }
            } else {
                userFileState.setLastReadAt(java.time.LocalDateTime.now());
                userFileState.setUpdatedAt(java.time.LocalDateTime.now());
                userFileStateService.updateById(userFileState);
            }
        } catch (Exception e) {
            // 记录日志但不中断流程
            System.err.println("记录阅读状态失败: " + e.getMessage());
        }
        return Result.success();
    }

    /**
     * 获取我的收藏文件列表
     */
    @GetMapping("/my-stars")
    public Result<List<AssetFile>> getMyStarredFiles(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size) {
        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        // 查询用户收藏，按置顶、排序、创建时间倒序
        LambdaQueryWrapper<UserFileStar> starWrapper = new LambdaQueryWrapper<>();
        starWrapper.eq(UserFileStar::getUserId, currentUserId);
        starWrapper.orderByDesc(UserFileStar::getIsPinned);
        starWrapper.orderByAsc(UserFileStar::getPinOrder);
        starWrapper.orderByDesc(UserFileStar::getCreatedAt);
        starWrapper.last("LIMIT " + (page - 1) * size + "," + size);

        List<UserFileStar> starredFiles = userFileStarService.list(starWrapper);

        if (starredFiles.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 获取文件ID列表
        List<Long> fileIds = starredFiles.stream()
                                        .map(UserFileStar::getFileId)
                                        .collect(Collectors.toList());

        // 根据文件ID查询 AssetFile 详情
        List<AssetFile> files = assetFileService.listByIds(fileIds);

        // 重新排序，确保按照收藏日志的顺序返回，并设置 isNew 字段
        Map<Long, AssetFile> fileMap = files.stream()
                                            .collect(Collectors.toMap(AssetFile::getId, f -> f));

        List<AssetFile> result = starredFiles.stream()
                                            .map(star -> {
                                                AssetFile file = fileMap.get(star.getFileId());
                                                if (file != null) {
                                                    file.setIsNew(isNewFile(file, currentUserId));
                                                    file.setIsStarred(true); // 既然在收藏列表里，肯定已收藏
                                                    file.setIsPinned(star.getIsPinned()); // 显式设置 isPinned 字段
                                                }
                                                return file;
                                            })
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.toList());

        return Result.success(result);
    }

    /**
     * 收藏文件
     */
    @PostMapping("/star/{fileId}")
    public Result<Void> starFile(@PathVariable Long fileId, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (fileId == null) {
            return Result.error("文件ID不能为空");
        }

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        LambdaQueryWrapper<UserFileStar> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFileStar::getUserId, currentUserId);
        wrapper.eq(UserFileStar::getFileId, fileId);
        UserFileStar userFileStar = userFileStarService.getOne(wrapper);

        if (userFileStar == null) {
            // 收藏
            userFileStar = new UserFileStar();
            userFileStar.setUserId(currentUserId);
            userFileStar.setFileId(fileId);
            userFileStar.setIsPinned(false); // 默认不置顶
            userFileStar.setPinOrder(0);
            userFileStar.setCreatedAt(java.time.LocalDateTime.now());
            userFileStarService.save(userFileStar);
        } else {
            // 已经收藏，不做处理，或者可以返回一个提示
            return Result.error("文件已收藏");
        }
        return Result.success();
    }

    /**
     * 取消收藏文件
     */
    @DeleteMapping("/star/{fileId}")
    public Result<Void> unstarFile(@PathVariable Long fileId, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (fileId == null) {
            return Result.error("文件ID不能为空");
        }

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        LambdaQueryWrapper<UserFileStar> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFileStar::getUserId, currentUserId);
        wrapper.eq(UserFileStar::getFileId, fileId);
        userFileStarService.remove(wrapper);
        return Result.success();
    }

    /**
     * 置顶或取消置顶收藏文件
     */
    @PostMapping("/star/{fileId}/pin")
    public Result<Void> pinFile(@PathVariable Long fileId,
                                @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @RequestParam(defaultValue = "true") boolean pin) {
        if (fileId == null) {
            return Result.error("文件ID不能为空");
        }

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        LambdaQueryWrapper<UserFileStar> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFileStar::getUserId, currentUserId);
        wrapper.eq(UserFileStar::getFileId, fileId);
        UserFileStar userFileStar = userFileStarService.getOne(wrapper);

        if (userFileStar == null) {
            return Result.error("文件未收藏，无法置顶");
        }

        userFileStar.setIsPinned(pin);
        // 如果置顶，可以设置一个默认的 pinOrder，或者让前端传入
        if (pin) {
            userFileStar.setPinOrder(1); // 默认置顶优先级最高
            userFileStar.setCreatedAt(java.time.LocalDateTime.now()); // 更新时间以触发重新排序
        } else {
            userFileStar.setPinOrder(0);
        }
        userFileStarService.updateById(userFileStar);
        return Result.success();
    }

    /**
     * 获取产品的核心资产列表
     */
    @GetMapping("/curated/{productId}")
    public Result<List<AssetFile>> getCuratedAssets(@PathVariable Long productId, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        LambdaQueryWrapper<AssetCurated> curatedWrapper = new LambdaQueryWrapper<>();
        curatedWrapper.eq(AssetCurated::getProductId, productId);
        curatedWrapper.orderByDesc(AssetCurated::getDisplayOrder); // 改为按 display_order 从大到小排序

        List<AssetCurated> curatedList = assetCuratedService.list(curatedWrapper);

        if (curatedList.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> fileIds = curatedList.stream()
                                        .map(AssetCurated::getFileId)
                                        .collect(Collectors.toList());

        List<AssetFile> files = assetFileService.listByIds(fileIds);

        // 重新排序，确保按照 curatedList 的顺序返回
        Map<Long, AssetFile> fileMap = files.stream()
                                            .collect(Collectors.toMap(AssetFile::getId, f -> f));

        // 如果前端传了 userId，则使用前端传的，否则使用默认 of 2L
        Long currentUserId = userId != null ? userId : 2L;

        List<AssetFile> result = curatedList.stream()
                                            .map(curated -> {
                                                AssetFile file = fileMap.get(curated.getFileId());
                                                if (file != null) {
                                                    file.setIsNew(isNewFile(file, currentUserId));
                                                    file.setIsStarred(isStarredFile(file, currentUserId));
                                                    file.setCurrentUserStarred(isStarredFile(file, currentUserId));
                                                }
                                                return file;
                                            })
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.toList());

        return Result.success(result);
    }

    /**
     * 获取全行最新更新文件列表
     */
    @GetMapping("/latest-updates")
    public Result<List<AssetFile>> getLatestUpdates(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size,
                                                     @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 查询最近 14 天内更新的文件，按更新时间倒序
        LambdaQueryWrapper<AssetFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(AssetFile::getUpdatedAt, java.time.LocalDateTime.now().minusDays(14));
        wrapper.eq(AssetFile::getNodeType, 2); // 只查询文件
        wrapper.eq(AssetFile::getIsLatest, 1); // 只显示最新版本
        wrapper.eq(AssetFile::getIsDeleted, 0); // 未删除
        wrapper.orderByDesc(AssetFile::getUpdatedAt);
        wrapper.last("LIMIT " + (page - 1) * size + "," + size);

        List<AssetFile> files = assetFileService.list(wrapper);

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;
        files.forEach(file -> {
            file.setIsNew(isNewFile(file, currentUserId));
            file.setIsStarred(isStarredFile(file, currentUserId));
            file.setCurrentUserStarred(isStarredFile(file, currentUserId));
        });

        return Result.success(files);
    }

    /**
     * 获取产品内使用频率最高的文件列表
     */
    @GetMapping("/product-use-top/{productId}")
    public Result<List<AssetFile>> getProductUseTop(@PathVariable Long productId,
                                                    @RequestParam(defaultValue = "10") int limit,
                                                    @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        List<Map<String, Object>> useTopList = assetAccessLogService.getProductUseTop(productId, limit);

        if (useTopList.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> fileIds = useTopList.stream()
                                       .map(map -> Long.valueOf(map.get("file_id").toString()))
                                       .collect(Collectors.toList());

        List<AssetFile> files = assetFileService.listByIds(fileIds);

        // 重新排序，确保按照 useTopList 的顺序返回
        Map<Long, AssetFile> fileMap = files.stream()
                                            .collect(Collectors.toMap(AssetFile::getId, f -> f));

        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        List<AssetFile> result = useTopList.stream()
                                           .map(map -> {
                                               Long fileId = Long.valueOf(map.get("file_id").toString());
                                               AssetFile file = fileMap.get(fileId);
                                               if (file != null) {
                                                    file.setIsNew(isNewFile(file, currentUserId));
                                                    file.setIsStarred(isStarredFile(file, currentUserId));
                                                    file.setCurrentUserStarred(isStarredFile(file, currentUserId));
                                               }
                                               return file;
                                           })
                                           .filter(Objects::nonNull)
                                           .collect(Collectors.toList());

        return Result.success(result);
    }

    /**
     * 批量获取资产详情
     */
    @PostMapping("/batch-details")
    public Result<List<AssetFile>> getBatchDetails(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        List<Long> ids = (List<Long>) body.get("ids");
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<AssetFile> files = assetFileService.listByIds(ids);
        
        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;
        
        files.forEach(file -> {
            file.setIsNew(isNewFile(file, currentUserId));
            file.setIsStarred(isStarredFile(file, currentUserId));
            file.setCurrentUserStarred(isStarredFile(file, currentUserId));
        });
        return Result.success(files);
    }

    /**
     * 批量打包下载资产
     */
    @PostMapping("/download")
    public void download(@RequestBody Map<String, List<Long>> body, HttpServletResponse response) {
        List<Long> ids = body.get("file_ids");
        if (ids == null || ids.isEmpty()) return;
        
        // 限制检查
        if (ids.size() > 50) {
            response.setStatus(400);
            try {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\": 400, \"message\": \"文件数量过多 (最多 50 个)\"}");
            } catch (IOException e) {}
            return;
        }
        
        List<AssetFile> selectedFiles = assetFileService.listByIds(ids);
        List<AssetFile> allFilesToDownload = new ArrayList<>();
        
        for (AssetFile file : selectedFiles) {
            if (file.getNodeType() == 1) { // 文件夹
                // 递归获取文件夹下所有文件
                List<AssetFile> children = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                        .like(AssetFile::getTreePath, "/" + file.getId() + "/")
                        .eq(AssetFile::getNodeType, 2)
                        .eq(AssetFile::getIsLatest, 1));
                allFilesToDownload.addAll(children);
            } else {
                allFilesToDownload.add(file);
            }
        }

        if (allFilesToDownload.isEmpty()) {
            response.setStatus(400);
            try {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\": 400, \"message\": \"没有可下载的文件\"}");
            } catch (IOException e) {}
            return;
        }

        long totalSize = allFilesToDownload.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0L).sum();
        if (totalSize > 500 * 1024 * 1024) { // 500MB
             response.setStatus(400);
             try {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\": 400, \"message\": \"总大小超过 500MB\"}");
            } catch (IOException e) {}
            return;
        }
        
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"assets.zip\"");
        
        List<String> missingFiles = new ArrayList<>();
        Set<String> addedPaths = new HashSet<>();
        Set<Long> processedFileIds = new HashSet<>();
        
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            for (AssetFile file : allFilesToDownload) {
                // 避免重复处理同一个文件记录（例如同时选中了文件夹和其下的文件）
                if (file.getId() != null && processedFileIds.contains(file.getId())) {
                    continue;
                }
                processedFileIds.add(file.getId());

                String localPath = file.getLocalPath();
                java.io.File physicalFile = (localPath != null && !localPath.isEmpty()) ? new java.io.File(localPath) : null;
                
                if (physicalFile == null || !physicalFile.exists()) {
                    missingFiles.add(file.getFileName() + " (ID: " + file.getId() + ", 路径: " + (localPath == null ? "空" : localPath) + ")");
                    continue;
                }

                // 处理重名问题
                String fileName = file.getFileName();
                String zipPath = fileName;
                int count = 1;
                while (addedPaths.contains(zipPath)) {
                    int dotIndex = fileName.lastIndexOf(".");
                    if (dotIndex != -1) {
                        zipPath = fileName.substring(0, dotIndex) + "(" + count + ")" + fileName.substring(dotIndex);
                    } else {
                        zipPath = fileName + "(" + count + ")";
                    }
                    count++;
                }
                addedPaths.add(zipPath);

                ZipEntry entry = new ZipEntry(zipPath);
                zos.putNextEntry(entry);
                
                try (FileInputStream fis = new FileInputStream(physicalFile)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
            
            // 始终生成一个下载报告，方便核对
            ZipEntry reportEntry = new ZipEntry("下载清单报告.txt");
            zos.putNextEntry(reportEntry);
            StringBuilder sb = new StringBuilder();
            sb.append("资产打包下载报告\n");
            sb.append("================\n");
            sb.append("生成时间: ").append(java.time.LocalDateTime.now()).append("\n");
            sb.append("成功打包文件数: ").append(addedPaths.size()).append("\n");
            sb.append("缺失文件数: ").append(missingFiles.size()).append("\n\n");
            
            if (!missingFiles.isEmpty()) {
                sb.append("缺失文件列表：\n");
                for (String m : missingFiles) {
                    sb.append("- ").append(m).append("\n");
                }
            } else {
                sb.append("所有文件均已成功打包。\n");
            }
            
            zos.write(sb.toString().getBytes("UTF-8"));
            zos.closeEntry();
            
            zos.finish();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取产品下的核心资产列表（DTO 格式）
     */
    @GetMapping("/product/{productId}/curated-assets")
    public Result<List<AssetCurated>> getProductCuratedAssets(@PathVariable Long productId) {
        List<AssetCurated> curatedAssets = assetCuratedService.getCuratedAssetsByProductId(productId);
        return Result.success(curatedAssets);
    }

    /**
     * 获取产品下的资产使用排行
     */
    @GetMapping("/product/{productId}/use-ranking")
    public Result<List<ProductUseRankingDTO>> getProductUseRanking(@PathVariable Long productId) {
        List<ProductUseRankingDTO> ranking = productUseRankingService.getProductUseRanking(productId);
        return Result.success(ranking);
    }
}

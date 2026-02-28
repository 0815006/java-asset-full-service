package com.asset.service.impl;

import com.asset.common.TreeUtils;
import com.asset.dto.AssetNodeDTO;
import com.asset.entity.AssetFileVersion;
import com.asset.entity.AssetNode;
import com.asset.mapper.AssetNodeMapper;
import com.asset.service.AssetFileVersionService;
import com.asset.service.AssetNodeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AssetNodeServiceImpl extends ServiceImpl<AssetNodeMapper, AssetNode> implements AssetNodeService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${storage.location.public}")
    private String publicStorage;

    @Value("${storage.location.internal}")
    private String internalStorage;

    @Value("${storage.location.confidential}")
    private String confidentialStorage;

    @Autowired
    private AssetFileVersionService assetFileVersionService;

    @Autowired
    private com.asset.service.SearchService searchService;

    @Override
    public List<AssetNodeDTO> getTree(Long productId) {
        List<AssetNode> nodes = this.lambdaQuery()
                .eq(AssetNode::getProductId, productId)
                .orderByAsc(AssetNode::getSortOrder)
                .list();

        return convertToTree(nodes);
    }

    @Override
    public List<AssetNodeDTO> getTreeByZone(String zoneType) {
        List<AssetNode> nodes = this.lambdaQuery()
                .eq(AssetNode::getZoneType, zoneType)
                .orderByAsc(AssetNode::getSortOrder)
                .list();

        return convertToTree(nodes);
    }

    private List<AssetNodeDTO> convertToTree(List<AssetNode> nodes) {
        List<AssetNodeDTO> dtos = nodes.stream().map(node -> {
            AssetNodeDTO dto = new AssetNodeDTO();
            BeanUtils.copyProperties(node, dto);
            return dto;
        }).collect(Collectors.toList());

        return TreeUtils.buildTree(dtos);
    }

    private String getStoragePath(String zoneType, Long productId) {
        String basePath;
        if ("product".equalsIgnoreCase(zoneType)) {
            basePath = confidentialStorage;
            if (productId != null) {
                return Paths.get(basePath).resolve("product_id_" + productId).toString();
            }
        } else if ("management".equalsIgnoreCase(zoneType)) {
            basePath = internalStorage;
        } else {
            basePath = publicStorage; // Default to tech/public
        }
        return basePath;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetNode upload(MultipartFile file, Long productId, Long parentId, String zoneType) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new RuntimeException("Filename cannot be null");
        }

        AssetNode node = this.lambdaQuery()
                .eq(AssetNode::getProductId, productId)
                .eq(AssetNode::getParentId, parentId)
                .eq(AssetNode::getName, originalFilename)
                .one();

        String storagePath = getStoragePath(zoneType, productId);
        String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
        Path path = Paths.get(storagePath).resolve(fileName);
        try {
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            file.transferTo(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }

        if (node == null) {
            node = new AssetNode();
            node.setName(originalFilename);
            node.setProductId(productId);
            node.setParentId(parentId);
            node.setType("FILE");
            node.setZoneType(zoneType);
            String extension = "";
            int i = originalFilename.lastIndexOf('.');
            if (i > 0) {
                extension = originalFilename.substring(i + 1);
            }
            node.setFileExtension(extension);
            node.setFileSize(file.getSize());
            node.setFilePath(path.toString());
            node.setCurrentVersion("v1.0");
            node.setCreatedAt(LocalDateTime.now());
            node.setUpdatedAt(LocalDateTime.now());
            this.save(node);
        } else {
            // If zone type changes, we might want to move the file, but for now let's just update the new version location
            // Or should we update the zoneType of the node? Let's assume zoneType is updated if provided.
            if (zoneType != null && !zoneType.isEmpty()) {
                node.setZoneType(zoneType);
            }
            
            String currentVersion = node.getCurrentVersion();
            // Simple version increment logic
            String newVersion = "v" + (System.currentTimeMillis()); 
            
            node.setCurrentVersion(newVersion);
            node.setFileSize(file.getSize());
            node.setFilePath(path.toString());
            node.setUpdatedAt(LocalDateTime.now());
            this.updateById(node);
        }

        AssetFileVersion fileVersion = new AssetFileVersion();
        fileVersion.setAssetNodeId(node.getId());
        fileVersion.setVersionNo(node.getCurrentVersion());
        fileVersion.setFilePath(path.toString());
        fileVersion.setUploadTime(LocalDateTime.now());
        assetFileVersionService.save(fileVersion);

        // Index to Solr
        searchService.index(node);

        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssetNode createFolder(String name, Long productId, Long parentId) {
        AssetNode node = new AssetNode();
        node.setName(name);
        node.setProductId(productId);
        node.setParentId(parentId);
        node.setType("FOLDER");
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        this.save(node);
        return node;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean renameNode(Long id, String newName) {
        AssetNode node = this.getById(id);
        if (node == null) {
            return false;
        }
        node.setName(newName);
        node.setUpdatedAt(LocalDateTime.now());
        boolean result = this.updateById(node);
        if (result) {
            searchService.index(node);
        }
        return result;
    }

    @Override
    public String getNodePath(Long id) {
        List<String> pathNames = new ArrayList<>();
        AssetNode currentNode = this.getById(id);
        while (currentNode != null) {
            pathNames.add(currentNode.getName());
            if (currentNode.getParentId() == null || currentNode.getParentId() == 0) {
                break;
            }
            currentNode = this.getById(currentNode.getParentId());
        }
        Collections.reverse(pathNames);
        return String.join("/", pathNames);
    }

    @Override
    public Resource preview(Long id) {
        AssetNode node = this.getById(id);
        if (node == null) {
            throw new RuntimeException("Node not found");
        }
        Path path = Paths.get(node.getFilePath());
        return new FileSystemResource(path);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContent(Long id, String content) {
        AssetNode node = this.getById(id);
        if (node == null) {
            throw new RuntimeException("Node not found");
        }

        // Use existing zone type or default to public if not set
        String zoneType = node.getZoneType();
        String storagePath = getStoragePath(zoneType, node.getProductId());

        String fileName = UUID.randomUUID().toString() + "_" + node.getName();
        Path path = Paths.get(storagePath).resolve(fileName);
        try {
             if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file", e);
        }

        String newVersion = "v" + (System.currentTimeMillis());
        node.setCurrentVersion(newVersion);
        node.setFilePath(path.toString());
        try {
            node.setFileSize(Files.size(path));
        } catch (IOException e) {
             // ignore
        }
        node.setUpdatedAt(LocalDateTime.now());
        this.updateById(node);

        AssetFileVersion fileVersion = new AssetFileVersion();
        fileVersion.setAssetNodeId(node.getId());
        fileVersion.setVersionNo(node.getCurrentVersion());
        fileVersion.setFilePath(path.toString());
        fileVersion.setUploadTime(LocalDateTime.now());
        assetFileVersionService.save(fileVersion);
        
        // Index to Solr
        searchService.index(node);
    }

    @Override
    public void batchDownload(List<Long> ids, OutputStream outputStream) {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            List<AssetNode> nodes = this.listByIds(ids);
            for (AssetNode node : nodes) {
                if (node.getFilePath() != null) {
                    Path path = Paths.get(node.getFilePath());
                    if (Files.exists(path)) {
                        ZipEntry zipEntry = new ZipEntry(node.getName());
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to zip files", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteNode(Long id) {
        AssetNode node = this.getById(id);
        if (node == null) {
            return false;
        }
        
        // Check if fixed
        if (Boolean.TRUE.equals(node.getIsFixed())) {
            throw new RuntimeException("Cannot delete fixed node");
        }

        // Delete physical file
        if (node.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(node.getFilePath()));
            } catch (IOException e) {
                // Log warning but continue? Or throw?
                e.printStackTrace();
            }
        }

        // Delete from Solr
        searchService.delete(id);

        // Delete from DB
        return this.removeById(id);
    }
}

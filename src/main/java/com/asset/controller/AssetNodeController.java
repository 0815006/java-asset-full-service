package com.asset.controller;

import com.asset.common.Result;
import com.asset.dto.AssetNodeDTO;
import com.asset.entity.AssetNode;
import com.asset.service.AssetNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/asset-node")
public class AssetNodeController {

    @Autowired
    private AssetNodeService assetNodeService;

    @GetMapping("/tree/{productId}")
    public Result<List<AssetNodeDTO>> getTree(@PathVariable Long productId) {
        return Result.success(assetNodeService.getTree(productId));
    }

    @GetMapping("/tech-tree")
    public Result<List<AssetNodeDTO>> getTechTree() {
        return Result.success(assetNodeService.getTreeByZone("tech"));
    }

    @GetMapping("/management-tree")
    public Result<List<AssetNodeDTO>> getManagementTree() {
        return Result.success(assetNodeService.getTreeByZone("mgmt"));
    }

    @PostMapping("/upload")
    public Result<AssetNode> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("productId") Long productId,
                                    @RequestParam("parentId") Long parentId,
                                    @RequestParam(value = "zoneType", required = false, defaultValue = "public") String zoneType) {
        return Result.success(assetNodeService.upload(file, productId, parentId, zoneType));
    }

    @PostMapping("/create-folder")
    public Result<AssetNode> createFolder(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        Long productId = Long.valueOf(payload.get("productId").toString());
        Long parentId = Long.valueOf(payload.get("parentId").toString());
        return Result.success(assetNodeService.createFolder(name, productId, parentId));
    }

    @PostMapping("/rename")
    public Result<Boolean> renameNode(@RequestBody Map<String, Object> payload) {
        Long id = Long.valueOf(payload.get("id").toString());
        String newName = (String) payload.get("newName");
        return Result.success(assetNodeService.renameNode(id, newName));
    }

    @GetMapping("/path/{id}")
    public Result<String> getNodePath(@PathVariable Long id) {
        return Result.success(assetNodeService.getNodePath(id));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        Resource resource = assetNodeService.preview(id);
        // Simple content type detection or default
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws UnsupportedEncodingException {
        AssetNode node = assetNodeService.getById(id);
        Resource resource = assetNodeService.preview(id);
        String fileName = URLEncoder.encode(node.getName(), StandardCharsets.UTF_8.toString());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/batch-download")
    public void batchDownload(@RequestParam("ids") List<Long> ids, HttpServletResponse response) {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"batch_download.zip\"");
        try {
            assetNodeService.batchDownload(ids, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download files", e);
        }
    }

    @PostMapping("/update-content")
    public Result<Boolean> updateContent(@RequestBody Map<String, Object> payload) {
        Long id = Long.valueOf(payload.get("id").toString());
        String content = payload.get("content").toString();
        assetNodeService.updateContent(id, content);
        return Result.success(true);
    }

    @GetMapping
    public Result<List<AssetNode>> list() {
        return Result.success(assetNodeService.list());
    }

    @PostMapping
    public Result<Boolean> save(@RequestBody AssetNode assetNode) {
        return Result.success(assetNodeService.save(assetNode));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody AssetNode assetNode) {
        return Result.success(assetNodeService.updateById(assetNode));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(assetNodeService.deleteNode(id));
    }

    @GetMapping("/{id}")
    public Result<AssetNode> getById(@PathVariable Long id) {
        return Result.success(assetNodeService.getById(id));
    }
}

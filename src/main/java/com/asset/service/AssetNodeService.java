package com.asset.service;

import com.asset.dto.AssetNodeDTO;
import com.asset.entity.AssetNode;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AssetNodeService extends IService<AssetNode> {
    List<AssetNodeDTO> getTree(Long productId);
    List<AssetNodeDTO> getTreeByZone(String zoneType);
    AssetNode upload(MultipartFile file, Long productId, Long parentId, String zoneType);
    AssetNode createFolder(String name, Long productId, Long parentId);
    boolean renameNode(Long id, String newName);
    String getNodePath(Long id);
    Resource preview(Long id);
    void updateContent(Long id, String content);
    void batchDownload(List<Long> ids, java.io.OutputStream outputStream);
    boolean deleteNode(Long id);
}

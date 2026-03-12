package com.asset.service.impl;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.mapper.AssetFileMapper;
import com.asset.service.AssetFileService;
import com.asset.service.SearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private SolrClient solrClient;

    @Autowired
    private AssetFileMapper assetFileMapper;

    @Autowired
    private AssetFileService assetFileService;

    private volatile boolean isRebuilding = false;
    private volatile int rebuildTotal = 0;
    private volatile int rebuildCurrent = 0;

    private Long techRootId = null;
    private Long mgmtRootId = null;

    @PostConstruct
    public void init() {
        cacheRootIds();
    }

    private synchronized void cacheRootIds() {
        if (techRootId == null) {
            List<AssetFile> techRoots = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getFileName, "测试技术及工艺专区")
                    .eq(AssetFile::getProductId, 0L)
                    .eq(AssetFile::getParentId, 0L)
                    .last("limit 1"));
            if (techRoots != null && !techRoots.isEmpty()) {
                techRootId = techRoots.get(0).getId();
                System.out.println("Cached '测试技术及工艺专区' root ID: " + techRootId);
            } else {
                System.err.println("Could not find '测试技术及工艺专区' root folder in database.");
            }
        }
        if (mgmtRootId == null) {
            List<AssetFile> mgmtRoots = assetFileService.list(new LambdaQueryWrapper<AssetFile>()
                    .eq(AssetFile::getFileName, "测试管理专区")
                    .eq(AssetFile::getProductId, 0L)
                    .eq(AssetFile::getParentId, 0L)
                    .last("limit 1"));
            if (mgmtRoots != null && !mgmtRoots.isEmpty()) {
                mgmtRootId = mgmtRoots.get(0).getId();
                System.out.println("Cached '测试管理专区' root ID: " + mgmtRootId);
            } else {
                System.err.println("Could not find '测试管理专区' root folder in database.");
            }
        }
    }

    private String getZoneType(AssetFile node) {
        if (node.getProductId() != null && node.getProductId() != 0) {
            return "product";
        }
        
        if (techRootId == null || mgmtRootId == null) {
            cacheRootIds(); // Retry caching if it failed on startup
        }

        if (techRootId != null && node.getId().equals(techRootId)) return "tech";
        if (mgmtRootId != null && node.getId().equals(mgmtRootId)) return "mgmt";

        String treePath = node.getTreePath();
        if (treePath != null) {
            if (techRootId != null && (treePath.startsWith("/0/" + techRootId) || node.getId().equals(techRootId))) {
                return "tech";
            }
            if (mgmtRootId != null && (treePath.startsWith("/0/" + mgmtRootId) || node.getId().equals(mgmtRootId))) {
                return "mgmt";
            }
        }
        
        return "unknown";
    }

    private boolean isParsable(String ext) {
        if (ext == null) return true;
        String lowerExt = ext.toLowerCase();
        // 排除图片
        if (lowerExt.matches("^(jpg|jpeg|png|gif|bmp|tiff|webp|svg|ico)$")) return false;
        // 排除压缩包
        if (lowerExt.matches("^(zip|rar|tar|gz|7z|bz2|xz)$")) return false;
        // 排除音视频
        if (lowerExt.matches("^(mp3|mp4|avi|mkv|mov|wav|flac)$")) return false;
        // 排除可执行文件
        if (lowerExt.matches("^(exe|dll|so|bin|apk|dmg)$")) return false;
        return true;
    }

    @Override
    public void index(AssetFile node) {
        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id", node.getId().toString());
            doc.addField("name", node.getFileName());
            
            if (node.getProductId() != null) {
                doc.addField("product_id", node.getProductId());
            }
            if (node.getExt() != null) {
                doc.addField("ext", node.getExt());
            }
            if (node.getTreePath() != null) {
                doc.addField("tree_path", node.getTreePath());
            }
            
            // Add zone_type
            doc.addField("zone_type", getZoneType(node));

            String content = "";
            try {
                if (node.getLocalPath() != null && isParsable(node.getExt())) {
                    Tika tika = new Tika();
                    // 设置最大提取长度为 10MB，防止超大文件导致内存溢出
                    tika.setMaxStringLength(10 * 1024 * 1024);
                    content = tika.parseToString(Paths.get(node.getLocalPath()));
                    if (content != null) {
                        // 清理多余的空白字符，减小索引体积
                        content = content.replaceAll("\\s+", " ").trim();
                    }
                } else {
                    content = "File content for " + node.getFileName();
                }
            } catch (Exception e) {
                System.err.println("Tika 解析文件失败: " + node.getFileName() + ", " + e.getMessage());
                content = "Error reading content";
            }
            doc.addField("text", content);

            solrClient.add("file_search", doc);
            solrClient.commit("file_search");
        } catch (Exception e) {
            System.err.println("Solr 索引失败 (Solr 可能未启动): " + e.getMessage());
        }
    }

    @Override
    public void delete(Long id) {
        try {
            solrClient.deleteById("file_search", id.toString());
            solrClient.commit("file_search");
        } catch (Exception e) {
            System.err.println("Solr 删除索引失败 (Solr 可能未启动): " + e.getMessage());
        }
    }

    @Override
    public void deleteBySolrId(String solrId) {
        try {
            solrClient.deleteById("file_search", solrId);
            solrClient.commit("file_search");
        } catch (Exception e) {
            System.err.println("Solr 删除索引失败 (Solr 可能未启动): " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getAllIndexedDocuments() {
        try {
            SolrQuery query = new SolrQuery("*:*");
            query.setRows(10000); // 假设最多 10000 个文档
            QueryResponse response = solrClient.query("file_search", query);
            SolrDocumentList results = response.getResults();
            
            List<Map<String, Object>> list = new ArrayList<>();
            for (SolrDocument doc : results) {
                Map<String, Object> map = new HashMap<>();
                for (String field : doc.getFieldNames()) {
                    map.put(field, doc.getFieldValue(field));
                }
                list.add(map);
            }
            return list;
        } catch (Exception e) {
            System.err.println("获取 Solr 所有文档失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public Result<List<Map<String, Object>>> search(String keyword, String searchZoneType, Long productId, int page, int size) {
        try {
            SolrQuery query = new SolrQuery();
            StringBuilder queryStr = new StringBuilder();
            
            if (keyword == null || keyword.trim().isEmpty()) {
                queryStr.append("*:*");
            } else {
                queryStr.append("text:").append(keyword);
            }
            
            if (productId != null) {
                queryStr.append(" AND product_id:").append(productId);
            }

            if (searchZoneType != null && !searchZoneType.trim().isEmpty()) {
                queryStr.append(" AND zone_type:").append(searchZoneType);
            }
            
            query.setQuery(queryStr.toString());
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                query.setHighlight(true);
                query.addHighlightField("text");
                query.setHighlightSimplePre("<em style='color:red'>");
                query.setHighlightSimplePost("</em>");
                query.setHighlightSnippets(1);
                query.setHighlightFragsize(100);
            }
            query.setStart((page - 1) * size);
            query.setRows(size);

            QueryResponse response = solrClient.query("file_search", query);
            SolrDocumentList results = response.getResults();
            Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();

            List<Map<String, Object>> list = new ArrayList<>();
            for (SolrDocument doc : results) {
                Map<String, Object> map = new HashMap<>();
                for (String field : doc.getFieldNames()) {
                    // Solr 返回的字段值可能是 List<String>，需要特殊处理
                    Object fieldValue = doc.getFieldValue(field);
                    if (fieldValue instanceof java.util.Collection) {
                        java.util.Collection<?> col = (java.util.Collection<?>) fieldValue;
                        map.put(field, col.isEmpty() ? null : col.iterator().next());
                    } else if (fieldValue != null && fieldValue.getClass().isArray()) {
                        Object[] arr = (Object[]) fieldValue;
                        map.put(field, arr.length == 0 ? null : arr[0]);
                    } else {
                        map.put(field, fieldValue);
                    }
                }

                // 区域名称翻译
                String zoneType = (String) map.get("zone_type");
                if ("tech".equals(zoneType)) {
                    map.put("zone_name", "测试技术与工艺专区");
                } else if ("mgmt".equals(zoneType)) {
                    map.put("zone_name", "测试管理专区");
                } else if ("product".equals(zoneType) && map.get("product_id") != null) {
                    // 产品名称由 SearchController 负责回填
                    map.put("zone_name", "产品专区"); // 暂时显示为产品专区，待 SearchController 填充具体产品名
                } else {
                    map.put("zone_name", "未知区域");
                }

                if (highlighting != null) {
                    String id = (String) map.get("id");
                    if (highlighting.containsKey(id)) {
                        Map<String, List<String>> highlights = highlighting.get(id);
                        if (highlights != null && highlights.containsKey("text")) {
                            List<String> snippets = highlights.get("text");
                            if (snippets != null && !snippets.isEmpty()) {
                                map.put("highlight", snippets.get(0));
                            }
                        }
                    }
                }
                list.add(map);
            }
            return Result.success(list);
        } catch (Exception e) {
            System.err.println("Solr 搜索失败，尝试切换到数据库模糊搜索: " + e.getMessage());
            // Fallback does not support zoneType yet, but it's a fallback.
            return databaseSearchFallback(keyword, searchZoneType, productId, page, size);
        }
    }

    @Override
    public void startRebuildAll() {
        if (isRebuilding) {
            return;
        }
        isRebuilding = true;
        rebuildTotal = 0;
        rebuildCurrent = 0;

        new Thread(() -> {
            try {
                LambdaQueryWrapper<AssetFile> wrapper = new LambdaQueryWrapper<AssetFile>()
                        .eq(AssetFile::getIsLatest, 1)
                        .eq(AssetFile::getNodeType, 2);
                List<AssetFile> files = assetFileMapper.selectList(wrapper);
                
                rebuildTotal = files.size();
                
                for (AssetFile file : files) {
                    index(file);
                    rebuildCurrent++;
                }
            } catch (Exception e) {
                System.err.println("重建索引失败: " + e.getMessage());
            } finally {
                isRebuilding = false;
            }
        }).start();
    }

    @Override
    public Map<String, Object> getRebuildProgress() {
        Map<String, Object> progress = new HashMap<>();
        progress.put("isRebuilding", isRebuilding);
        progress.put("total", rebuildTotal);
        progress.put("current", rebuildCurrent);
        return progress;
    }

    private Result<List<Map<String, Object>>> databaseSearchFallback(String keyword, String zoneType, Long productId, int page, int size) {
        LambdaQueryWrapper<AssetFile> wrapper = new LambdaQueryWrapper<AssetFile>()
                .eq(AssetFile::getIsLatest, 1)
                .eq(AssetFile::getNodeType, 2);
        
        if (productId != null && productId != 0) {
            wrapper.eq(AssetFile::getProductId, productId);
        }

        // Fallback search also filters by zoneType
        if ("tech".equals(zoneType)) {
            if (techRootId == null) cacheRootIds();
            if (techRootId != null) {
                wrapper.and(w -> w.eq(AssetFile::getId, techRootId).or().like(AssetFile::getTreePath, "/" + techRootId + "/"));
            }
        } else if ("mgmt".equals(zoneType)) {
            if (mgmtRootId == null) cacheRootIds();
            if (mgmtRootId != null) {
                wrapper.and(w -> w.eq(AssetFile::getId, mgmtRootId).or().like(AssetFile::getTreePath, "/" + mgmtRootId + "/"));
            }
        }
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(AssetFile::getFileName, keyword);
        }
        
        // 简单分页
        List<AssetFile> files = assetFileMapper.selectList(wrapper);
        
        List<Map<String, Object>> list = new ArrayList<>();
        for (AssetFile file : files) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", file.getId().toString());
            map.put("name", file.getFileName());
            map.put("ext", file.getExt());
            map.put("product_id", file.getProductId());
            map.put("tree_path", file.getTreePath());
            map.put("zone_type", getZoneType(file)); // Add zone_type for fallback results
            map.put("highlight", "文件名匹配: " + file.getFileName());
            list.add(map);
        }
        return Result.success(list);
    }
}

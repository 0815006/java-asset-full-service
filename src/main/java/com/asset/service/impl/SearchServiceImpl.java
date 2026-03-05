package com.asset.service.impl;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import com.asset.service.SearchService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private SolrClient solrClient;

    @Override
    public void index(AssetFile node) {
        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id", node.getId().toString());
            doc.addField("name", node.getFileName());
            
            if (node.getProductId() != null) {
                doc.addField("product_id", node.getProductId());
            }
            
            String content = "";
            try {
                if (node.getLocalPath() != null && (node.getFileName().endsWith(".txt") || node.getFileName().endsWith(".md"))) {
                    content = new String(Files.readAllBytes(Paths.get(node.getLocalPath())));
                } else {
                    content = "File content for " + node.getFileName();
                }
            } catch (Exception e) {
                content = "Error reading content";
            }
            doc.addField("text", content);

            solrClient.add("file_search", doc);
            solrClient.commit("file_search");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void delete(Long id) {
        try {
            solrClient.deleteById("file_search", id.toString());
            solrClient.commit("file_search");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Result<List<Map<String, Object>>> search(String keyword, Long productId, int page, int size) {
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
                    map.put(field, doc.getFieldValue(field));
                }

                if (highlighting != null) {
                    String id = (String) doc.getFieldValue("id");
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
            e.printStackTrace();
            return Result.error("Search failed: " + e.getMessage());
        }
    }
}

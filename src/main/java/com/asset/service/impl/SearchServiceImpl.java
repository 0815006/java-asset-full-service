package com.asset.service.impl;

import com.asset.common.Result;
import com.asset.service.SearchService;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrInputDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private SolrClient solrClient;

    @Override
    public void index(com.asset.entity.AssetNode node) {
        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.addField("id", node.getId().toString());
            doc.addField("name", node.getName());
            doc.addField("zone_type", node.getZoneType());
            if (node.getProductId() != null) {
                doc.addField("product_id", node.getProductId());
            }
            
            // Simple content extraction for text files
            String content = "";
            try {
                if (node.getFilePath() != null && (node.getName().endsWith(".txt") || node.getName().endsWith(".md"))) {
                    content = new String(Files.readAllBytes(Paths.get(node.getFilePath())));
                } else {
                    content = "File content for " + node.getName(); // Placeholder for binary files
                }
            } catch (Exception e) {
                content = "Error reading content";
            }
            doc.addField("text", content);

            solrClient.add("asset_core", doc);
            solrClient.commit("asset_core");
        } catch (Exception e) {
            e.printStackTrace();
            // Log error but don't block main flow? Or throw?
            // For now, print stack trace
        }
    }

    @Override
    public void delete(Long id) {
        try {
            solrClient.deleteById("asset_core", id.toString());
            solrClient.commit("asset_core");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Result<List<Map<String, Object>>> search(String keyword, int page, int size) {
        try {
            SolrQuery query = new SolrQuery();
            if (keyword == null || keyword.trim().isEmpty()) {
                query.setQuery("*:*");
            } else {
                query.setQuery("text:" + keyword);
                query.setHighlight(true);
                query.addHighlightField("text");
                query.setHighlightSimplePre("<em style='color:red'>");
                query.setHighlightSimplePost("</em>");
                query.setHighlightSnippets(1);
                query.setHighlightFragsize(100);
            }
            query.setStart((page - 1) * size);
            query.setRows(size);

            QueryResponse response = solrClient.query("asset_core", query);
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

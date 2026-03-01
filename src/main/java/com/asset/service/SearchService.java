package com.asset.service;

import com.asset.common.Result;
import java.util.List;
import java.util.Map;

public interface SearchService {
    Result<List<Map<String, Object>>> search(String keyword, Long productId, int page, int size);

    void index(com.asset.entity.AssetNode node);
    
    void delete(Long id);
}

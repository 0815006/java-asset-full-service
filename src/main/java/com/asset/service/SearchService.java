package com.asset.service;

import com.asset.common.Result;
import com.asset.entity.AssetFile;
import java.util.List;
import java.util.Map;

public interface SearchService {
    Result<List<Map<String, Object>>> search(String keyword, Long productId, int page, int size);

    void index(AssetFile node);
    
    void delete(Long id);
}

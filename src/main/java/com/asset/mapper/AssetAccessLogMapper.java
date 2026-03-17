package com.asset.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.asset.entity.AssetAccessLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssetAccessLogMapper extends BaseMapper<AssetAccessLog> {

    @Select("SELECT file_id, count(id) as use_count FROM asset_access_log " +
            "WHERE created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY) " +
            "GROUP BY file_id ORDER BY use_count DESC LIMIT #{limit}")
    List<Map<String, Object>> selectGlobalUseTop(@Param("limit") int limit);

    @Select("SELECT file_id, count(id) as use_count FROM asset_access_log " +
            "WHERE product_id = #{productId} AND created_at >= DATE_SUB(NOW(), INTERVAL 14 DAY) " +
            "GROUP BY file_id ORDER BY use_count DESC LIMIT #{limit}")
    List<Map<String, Object>> selectProductUseTop(@Param("productId") Long productId, @Param("limit") int limit);
}

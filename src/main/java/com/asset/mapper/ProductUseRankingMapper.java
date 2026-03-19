package com.asset.mapper;

import com.asset.dto.ProductUseRankingDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.asset.entity.AssetAccessLog; // Assuming AssetAccessLog entity exists or will be created
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductUseRankingMapper extends BaseMapper<AssetAccessLog> {

    @Select("SELECT aal.file_id AS fileId, af.file_name AS fileName, af.ext AS ext, COUNT(aal.id) AS useCount " +
            "FROM asset_access_log aal " +
            "JOIN asset_file af ON aal.file_id = af.id " +
            "WHERE aal.product_id = #{productId} " +
            "GROUP BY aal.file_id, af.file_name, af.ext " +
            "ORDER BY useCount DESC " +
            "LIMIT 10")
    List<ProductUseRankingDTO> getProductUseRanking(@Param("productId") Long productId);
}

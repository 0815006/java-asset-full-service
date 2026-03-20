package com.asset.mapper;

import com.asset.entity.AssetFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AssetFileMapper extends BaseMapper<AssetFile> {

    @Select("SELECT * FROM asset_file WHERE is_deleted = 1 ORDER BY updated_at DESC")
    List<AssetFile> selectDeletedFiles();

    @Select("SELECT * FROM asset_file WHERE id = #{id}")
    AssetFile selectByIdWithDeleted(Long id);

    @org.apache.ibatis.annotations.Delete("DELETE FROM asset_file WHERE id = #{id}")
    int deleteByIdPhysically(Long id);

    @org.apache.ibatis.annotations.Update("UPDATE asset_file SET is_deleted = 0, local_path = #{localPath} WHERE id = #{id}")
    int restoreById(@org.apache.ibatis.annotations.Param("id") Long id, @org.apache.ibatis.annotations.Param("localPath") String localPath);
}

package com.asset.mapper;

import com.asset.entity.UserFileStar;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserFileStarMapper extends BaseMapper<UserFileStar> {

    @Select("SELECT file_id, count(id) as star_count FROM user_file_star " +
            "GROUP BY file_id ORDER BY star_count DESC LIMIT #{limit}")
    List<Map<String, Object>> selectGlobalStarTop(@Param("limit") int limit);
}

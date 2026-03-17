package com.asset.service;

import com.asset.entity.UserFileStar;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface UserFileStarService extends IService<UserFileStar> {
    List<Map<String, Object>> getGlobalStarTop(int limit);
}

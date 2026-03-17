package com.asset.config;

import com.asset.service.AssetAccessLogService;
import com.asset.service.UserFileStateService;
import com.asset.entity.UserFileState;
import com.asset.entity.AssetAccessLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.asset.service.UserFileStarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Autowired
    private UserFileStateService userFileStateService;

    @Autowired
    private AssetAccessLogService assetAccessLogService;

    @Autowired
    private UserFileStarService userFileStarService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 每天凌晨 3 点执行
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanUpOldData() {
        LocalDateTime fifteenDaysAgo = LocalDateTime.now().minusDays(15);

        // 清理 user_file_state 表中 15 天前的阅读状态
        LambdaQueryWrapper<UserFileState> userFileStateWrapper = new LambdaQueryWrapper<>();
        userFileStateWrapper.lt(UserFileState::getLastReadAt, fifteenDaysAgo);
        userFileStateService.remove(userFileStateWrapper);
        System.out.println("清理了 user_file_state 表中 15 天前的记录。");

        // 清理 asset_access_log 表中 15 天前的访问日志
        LambdaQueryWrapper<AssetAccessLog> accessLogWrapper = new LambdaQueryWrapper<>();
        accessLogWrapper.lt(AssetAccessLog::getCreatedAt, fifteenDaysAgo);
        assetAccessLogService.remove(accessLogWrapper);
        System.out.println("清理了 asset_access_log 表中 15 天前的记录。");
    }

    // 每小时执行一次，聚合榜单数据
    @Scheduled(cron = "0 0 * * * ?")
    public void aggregateTopLists() {
        // 聚合“全行使用榜”
        List<Map<String, Object>> globalUseTop = assetAccessLogService.getGlobalUseTop(10);
        stringRedisTemplate.opsForValue().set("global_use_top", com.alibaba.fastjson.JSON.toJSONString(globalUseTop));
        System.out.println("聚合了全行使用榜数据。");

        // 聚合“资产人气榜”
        List<Map<String, Object>> globalStarTop = userFileStarService.getGlobalStarTop(10);
        stringRedisTemplate.opsForValue().set("global_star_top", com.alibaba.fastjson.JSON.toJSONString(globalStarTop));
        System.out.println("聚合了资产人气榜数据。");
    }
}

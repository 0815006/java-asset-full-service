package com.asset.controller;

import com.asset.common.Result;
import com.asset.entity.EditLock;
import com.asset.service.EditLockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 资产编辑锁控制器
 * 处理文件的独占编辑锁定、续期及解锁逻辑
 */
@RestController
@RequestMapping("/api/assets")
@CrossOrigin
public class EditLockController {

    @Autowired
    private EditLockService editLockService;

    /**
     * 获取编辑锁
     * @param id 文件ID
     * @param userId 用户ID
     * @return 包含锁定状态和凭证的 Result
     */
    @PostMapping("/{id}/lock")
    public Result<Map<String, Object>> acquireLock(@PathVariable Long id, @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 如果前端传了 userId，则使用前端传的，否则使用默认的 2L
        Long currentUserId = userId != null ? userId : 2L;

        // Check if locked by others and not expired
        EditLock existing = editLockService.getOne(new LambdaQueryWrapper<EditLock>()
                .eq(EditLock::getAssetFileId, id)
                .gt(EditLock::getExpiresAt, LocalDateTime.now()));

        if (existing != null && !existing.getLockedBy().equals(currentUserId)) {
            return Result.error("File is locked by another user");
        }

        String ticket = UUID.randomUUID().toString();
        
        if (existing != null) {
            // Refresh lock
            existing.setLockTicket(ticket);
            existing.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            editLockService.updateById(existing);
        } else {
            // Create lock
            EditLock lock = new EditLock();
            lock.setAssetFileId(id);
            lock.setLockedBy(currentUserId);
            lock.setLockTicket(ticket);
            lock.setLockedAt(LocalDateTime.now());
            lock.setExpiresAt(LocalDateTime.now().plusMinutes(30));
            editLockService.save(lock);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("locked", true);
        res.put("lock_id", ticket);
        res.put("expires_in_seconds", 1800);
        
        return Result.success(res);
    }

    /**
     * 编辑锁续期
     * @param id 文件ID
     * @param body 包含 lock_id
     * @return 续期结果
     */
    @PutMapping("/{id}/lock/keepalive")
    public Result<Map<String, Object>> keepAlive(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String ticket = body.get("lock_id");
        EditLock lock = editLockService.getOne(new LambdaQueryWrapper<EditLock>()
                .eq(EditLock::getAssetFileId, id)
                .eq(EditLock::getLockTicket, ticket));

        if (lock == null) {
            return Result.error("Lock invalid or expired");
        }

        lock.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        editLockService.updateById(lock);

        Map<String, Object> res = new HashMap<>();
        res.put("expires_in_seconds", 1800);
        return Result.success(res);
    }

    /**
     * 释放编辑锁
     * @param id 文件ID
     * @param body 包含 lock_id
     * @return 操作结果
     */
    @PostMapping("/{id}/unlock")
    public Result<Void> unlock(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String ticket = body.get("lock_id");
        editLockService.remove(new LambdaQueryWrapper<EditLock>()
                .eq(EditLock::getAssetFileId, id)
                .eq(EditLock::getLockTicket, ticket));
        return Result.success();
    }
}

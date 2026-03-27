package com.asset.controller;

import com.asset.common.Result;
import com.asset.common.TokenUtils;
import com.asset.dto.ChangePasswordDTO;
import com.asset.entity.User;
import com.asset.entity.SysUser;
import com.asset.service.UserService;
import com.asset.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录认证控制器
 * 处理用户登录、Token 生成及密码修改
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin
public class LoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${auth.verify-internal-password:false}")
    private boolean verifyInternalPassword;

    @Value("${auth.user-source:asset}")
    private String userSource;

    /**
     * 用户登录
     * @param loginData 包含 username, password (AES密文), md5Password (MD5密文)
     * @return 包含 Token 和用户信息
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String aesPassword = loginData.get("password");
        String md5Password = loginData.get("md5Password");

        log.info("Attempting to log in user: {}, source: {}", username, userSource);
        
        // 1. 无论哪种模式，系统主体信息必须存在于 asset_user 表中
        User user = userService.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            log.warn("Login failed: user '{}' not found in asset_user table", username);
            return Result.error("用户未在资产库系统注册");
        }
        
        // 2. 校验逻辑
        boolean isPasswordCorrect = false;
        
        // 角色逻辑：role_type = 4 (外购人员) 永远校验密码；1,2,3 根据配置决定
        boolean needVerify = (user.getRoleType() != null && user.getRoleType() == 4) || verifyInternalPassword;

        if (!needVerify) {
            isPasswordCorrect = true;
        } else {
            if ("sys".equalsIgnoreCase(userSource)) {
                // 使用 sys_user 表进行 MD5 密文比对
                SysUser sysUser = sysUserService.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUserId, username));
                if (sysUser == null) {
                    log.warn("Login failed: user '{}' not found in sys_user table", username);
                    return Result.error("系统用户信息不存在");
                }
                isPasswordCorrect = md5Password != null && md5Password.equals(sysUser.getPassword());
            } else {
                // 使用 asset_user 表进行 AES 密文比对 (直接比对字符串)
                isPasswordCorrect = aesPassword != null && aesPassword.equals(user.getPasswordHash());
            }
        }

        if (!isPasswordCorrect) {
            log.warn("Login failed for user '{}': incorrect password", username);
            return Result.error("密码错误");
        }

        log.info("Login successful for user '{}'", username);
        Map<String, Object> data = new HashMap<>();
        data.put("token", tokenUtils.generateToken(user.getId()));
        data.put("user", user);

        return Result.success(data);
    }

    /**
     * 修改用户密码
     * @param changePasswordDTO 包含旧密码和新密码
     * @param request HTTP 请求（用于提取当前用户 ID）
     * @return 操作结果
     */
    @PostMapping("/user/change-password")
    public Result<String> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO, HttpServletRequest request) {
        Integer userId = tokenUtils.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error("用户未登录或Token无效");
        }
        // 调用Service层处理密码修改逻辑
        boolean success = userService.changePassword(userId, changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());
        if (success) {
            return Result.success("密码修改成功");
        } else {
            return Result.error("旧密码不正确或密码修改失败");
        }
    }
}

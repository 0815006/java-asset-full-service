package com.asset.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private TokenUtils tokenUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 请求（跨域预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 放行登录接口
        String uri = request.getRequestURI();
        if (uri.endsWith("/login")) {
            return true;
        }

        // 如果 Spring Security 已经认证，则直接放行
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null &&
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            return true;
        }

        // 否则，执行原有的 Token 校验逻辑
        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // 尝试从 URL 参数中获取 token (用于文件预览/下载等场景)
            token = request.getParameter("token");
        }

        if (token != null && !token.isEmpty()) {
            Long userId = tokenUtils.verifyToken(token);
            if (userId != null) {
                // 将 userId 存入 request 方便后续使用
                request.setAttribute("currentUserId", userId);
                return true;
            }
        }

        // 校验失败，返回 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\": 401, \"message\": \"会话已过期，请重新登录\"}");
        return false;
    }
}

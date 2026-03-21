# 发布说明

## 版本 1.0.0 - 2026-03-21

### 新功能

*   **核心系统初步开发**：实现了基于 Spring Boot、Maven 和 MyBatis Plus 的后端核心应用，提供稳定的后端服务。
*   **用户管理模块**：开发了用户注册、登录、认证（通过 `LoginController.java`、`UserController.java`、`TokenUtils.java`）和授权功能。
*   **安全加密升级**：全面启用 `BCryptPasswordEncoder` 对用户密码进行加密存储和校验，提升系统安全性。
*   **资产总数自动统计**：实现了产品资产总数（`asset_count`）的自动更新逻辑，涵盖上传、删除、恢复及同步等操作。
*   **资产文件管理**：引入了资产文件管理功能，包括上传、下载、收藏（`UserFileStar.java`）和状态跟踪（`UserFileState.java`）。
*   **精选资产管理**：增加了精选内容的管理功能，用于组织和展示特定资产。
*   **产品管理**：实现了产品的增删改查操作，包括产品使用排名（`ProductUseRankingDTO.java`）和用户收藏产品（`UserFavoriteProduct.java`）。
*   **搜索功能**：集成了基于 Solr 的搜索功能（`SearchController.java`、`SearchService.java`、`06_Solr_Deploy_Guide.md`），并支持热门搜索追踪（`AssetHotSearch.java`）。
*   **编辑锁定机制**：实现了编辑锁定系统（`EditLockController.java`），以防止资产的并发修改。
*   **访问日志记录**：开发了记录资产访问日志的系统（`AssetAccessLog.java`），用于审计和分析。

### Bug 修复

*   **修复登录校验逻辑**：修正了 `LoginController` 中使用明文比对密码的 Bug，统一使用 `BCrypt` 校验。
*   **修复 Spring Security 认证冲突**：集成了 `JwtAuthenticationFilter`，解决了自定义 Token 拦截器与 Spring Security 之间的 `403 Forbidden` 冲突。
*   **修复端口占用问题**：解决了由于 Java 进程未正常退出导致的 `Address already in use` 启动异常。

### 改进

*   **统一 API 响应**：实现了全局 `Result<T>` 类，确保所有控制器提供一致的 API 响应格式。
*   **全局异常处理**：建立了 `GlobalExceptionHandler`，用于集中和标准化错误处理。
*   **数据库 Schema**：提供了 `db_v2.sql` 和 `init_data.sql`，并将初始密码更新为 BCrypt 密文。
*   **MyBatis Plus 增强**：利用 `MyMetaObjectHandler` 自动填充字段（例如，创建/更新时间戳）。
*   **配置管理**：配置了 `application.yml` 以适应不同环境（开发/生产），并集成了 `WebConfig` 用于 Web 相关设置。
*   **无状态认证优化**：配置 Spring Security 使用 `STATELESS` 会话管理，完美适配 JWT 认证模式。

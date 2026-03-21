# 发布说明

## 版本 1.0.0 - 2026-03-21

### 新功能

*   **核心系统初步开发**：实现了基于 Spring Boot、Maven 和 MyBatis Plus 的后端核心应用，提供稳定的后端服务。
*   **用户管理模块**：开发了用户注册、登录、认证（通过 `LoginController.java`、`UserController.java`、`TokenUtils.java`）和授权功能。
*   **资产文件管理**：引入了资产文件管理功能，包括上传、下载、收藏（`UserFileStar.java`）和状态跟踪（`UserFileState.java`）。
*   **精选资产管理**：增加了精选内容的管理功能，用于组织和展示特定资产。
*   **产品管理**：实现了产品的增删改查操作，包括产品使用排名（`ProductUseRankingDTO.java`）和用户收藏产品（`UserFavoriteProduct.java`）。
*   **搜索功能**：集成了基于 Solr 的搜索功能（`SearchController.java`、`SearchService.java`、`06_Solr_Deploy_Guide.md`），并支持热门搜索追踪（`AssetHotSearch.java`）。
*   **编辑锁定机制**：实现了编辑锁定系统（`EditLockController.java`），以防止资产的并发修改。
*   **访问日志记录**：开发了记录资产访问日志的系统（`AssetAccessLog.java`），用于审计和分析。

### Bug 修复

*   [初始版本未发现具体 Bug，此部分留待后续更新]

### 改进

*   **统一 API 响应**：实现了全局 `Result<T>` 类，确保所有控制器提供一致的 API 响应格式。
*   **全局异常处理**：建立了 `GlobalExceptionHandler`，用于集中和标准化错误处理。
*   **数据库 Schema**：提供了 `db_v2.sql` 和 `init_data.sql`，便于数据库设置和初始数据填充。
*   **MyBatis Plus 增强**：利用 `MyMetaObjectHandler` 自动填充字段（例如，创建/更新时间戳）。
*   **配置管理**：配置了 `application.yml` 以适应不同环境（开发/生产），并集成了 `WebConfig` 用于 Web 相关设置。

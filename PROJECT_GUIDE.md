# 后端项目指南 (Backend Project Guide)

## 1. 项目概述
**项目名称：** java-asset-full-service
**描述：** 资产管理系统后端服务，提供文件管理、存储隔离、全文检索、健康检查及在线预览支持等功能。基于 Spring Boot 框架构建，采用标准的分层架构。

## 2. 技术栈
- **开发语言：** Java `1.8`
- **核心框架：** Spring Boot `2.7.18`
- **ORM 框架：** MyBatis Plus `3.5.3.1`
- **数据库：** MySQL 8.0
- **搜索引擎：** Spring Boot Starter Data Solr `2.4.13`
- **文档解析：** Apache Tika `1.28.5`
- **构建工具：** Maven

## 3. 系统架构
本项目遵循标准的分层架构设计：

- **控制层 (Controller Layer - `com.asset.controller`)**
  - 处理 HTTP 请求，参数校验。
  - 返回统一的 `Result<T>` 响应结构。
- **业务层 (Service Layer - `com.asset.service`)**
  - 包含核心业务逻辑。
  - 事务管理。
- **持久层 (Mapper Layer - `com.asset.mapper`)**
  - 基于 MyBatis Plus 与数据库交互。
- **实体层 (Entity Layer - `com.asset.entity`)**
  - 对应数据库表结构。
  - 使用 Lombok 注解简化代码。

### 关键组件
- **全局异常处理 (`GlobalExceptionHandler`)**：捕获全局异常，返回统一的错误响应格式。
- **统一响应结果 (`Result<T>`)**：规范 API 返回结构，包含 `code`, `message`, `data`。

## 4. 功能模块与 API 逻辑

### 4.1 资产文件管理 (`AssetFileController`)
- **目录树**：支持按产品 (`/tree`) 或公共专区懒加载获取文件树。
- **文件操作**：上传、下载（单文件/打包）、更新（版本覆盖）、重命名、删除、移动。
- **在线预览**：通过 `/view` 接口提供文件流式输出，支持 PDF、图片、纯文本及 Office 文档（需配合 OnlyOffice）。
- **存储隔离**：根据 `zone_type`（`product_zone`, `tech_zone`, `mgmt_zone`）将文件存储在不同物理目录。

### 4.2 全文检索 (`SearchController`)
- 集成 Apache Solr，并提供数据库降级方案。
- **索引**：对资产元数据（ID、名称、区域、产品）和文件内容（通过 Tika 解析）建立索引。
- **搜索**：支持关键词高亮搜索，并按区域（`zoneType`）或产品（`productId`）进行过滤。

### 4.3 健康检查
- **存储健康检查 (`AssetFileController`)**：对比物理文件与数据库记录，找出“缺失”和“多余”的文件。
- **索引健康检查 (`SearchController`)**：对比数据库记录与 Solr 索引，找出“索引缺失”和“索引多余”的文档，并提供在线修复功能。

### 4.4 其他
- **产品管理 (`ProductController`)**：管理产品信息。
- **用户登录 (`LoginController`)**：提供基础的用户认证。

## 5. 配置说明
配置文件位于 `src/main/resources/application.yml`。

### 5.1 服务与数据库
- **服务端口：** 8081
- **数据库：** MySQL (`asset_db`)
- **时区：** Asia/Shanghai

### 5.2 Solr 配置
- **地址：** `http://localhost:8983/solr`
- **Core 名称：** `file_search`

### 5.3 文件存储
- **根目录：** `D:/data/asset_files` (可通过 `file.upload-dir` 修改)

## 6. 安装与运行

1.  **环境准备**
    - 启动 MySQL 8.0 服务。
    - 启动 Apache Solr 服务。
    - （可选）启动 OnlyOffice Document Server (Docker)。

2.  **数据库初始化**
    - 执行 `db_v2.sql` 创建数据库表结构。
    - 执行 `init_data.sql` 初始化基础数据。

3.  **修改配置**
    - 根据实际环境修改 `application.yml` 中的数据库连接信息和 Solr 地址。

4.  **构建与启动**
    ```bash
    # 构建项目
    mvn clean install
    # 启动服务
    mvn spring-boot:run
    ```
    或者直接在 IDE 中运行 `com.asset.AssetApplication` 主类。

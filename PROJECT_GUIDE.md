# 后端项目指南 (Backend Project Guide)

## 1. 项目概述
**项目名称：** java-asset-full-service
**描述：** 资产管理系统后端服务，提供文件管理、存储隔离、全文检索、知识图谱及系统管理等功能。基于 Spring Boot 框架构建，采用标准的分层架构。

## 2. 技术栈
- **开发语言：** Java 8+ (兼容 Java 17/21)
- **核心框架：** Spring Boot 2.7.18
- **ORM 框架：** MyBatis Plus 3.5.3.1
- **数据库：** MySQL 8.0
- **搜索引擎：** Apache Solr 2.4.13 (Spring Data Solr)
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
  - 继承 `BaseMapper` 提供基础 CRUD 功能。
- **实体层 (Entity Layer - `com.asset.entity`)**
  - 对应数据库表结构。
  - 使用 Lombok 注解 (`@Data`, `@Builder` 等) 简化代码。

### 关键组件
- **全局异常处理 (`GlobalExceptionHandler`)**：捕获全局异常，返回统一的错误响应格式。
- **统一响应结果 (`Result<T>`)**：规范 API 返回结构，包含 `code` (状态码), `message` (消息), `data` (数据)。
- **树形结构工具 (`TreeUtils`)**：用于构建资产目录树、组织架构树等。

## 4. 功能模块与 API 逻辑

### 4.1 资产管理 (`AssetNodeController`, `AssetFileVersionController`)
- **目录树**：支持按产品 (`/tree/{productId}`) 或技术栈 (`/tech-tree`) 获取文件树。
- **文件操作**：上传、下载、预览、重命名、删除、移动。
- **版本控制**：管理文件的多版本 (`AssetFileVersion`)。
- **存储隔离**：根据安全区域 (`zoneType`) 将文件存储在不同目录：
  - **公开区 (Public):** `uploads/public`
  - **内部区 (Internal):** `uploads/internal`
  - **绝密区 (Confidential):** `uploads/confidential`

### 4.2 全文检索 (`SearchController`)
- 集成 Apache Solr。
- **索引**：对资产元数据（ID、名称、区域、产品）和文件内容（文本/Markdown）建立索引。
- **搜索**：支持关键词高亮搜索。

### 4.3 业务功能
- **产品管理 (`BusiProductController`)**：管理产品信息。
- **知识图谱 (`BusiKnowledgeGraphController`)**：管理业务知识图谱数据。
- **用户收藏 (`BusiUserFavoriteController`)**：管理用户收藏的资产。

### 4.4 系统管理
- **用户管理 (`SysUserController`)**：系统用户增删改查。
- **团队管理 (`SysTeamController`)**：团队组织架构管理。
- **域管理 (`SysDomainController`)**：系统域/租户管理。

## 5. 配置说明
配置文件位于 `src/main/resources/application.yml`。

### 5.1 服务与数据库
- **服务端口：** 8081
- **数据库：** MySQL (`asset_db`)
- **时区：** Asia/Shanghai

### 5.2 Solr 配置
- **地址：** `http://localhost:8983/solr`
- **Core 名称：** `asset_core`

## 6. 安装与运行

1.  **数据库初始化**
    - 执行 `creat.sql` 创建数据库表结构。
    - 执行 `init_data.sql` 初始化基础数据。
2.  **Solr 环境准备**
    - 确保 Solr 服务运行在 8983 端口。
    - 创建名为 `asset_core` 的 Core。
3.  **修改配置**
    - 根据实际环境修改 `application.yml` 中的数据库连接信息 (URL, 用户名, 密码) 和 Solr 地址。
4.  **构建项目**
    ```bash
    mvn clean install
    ```
5.  **启动服务**
    - 运行 `com.asset.AssetApplication` 主类启动 Spring Boot 应用。

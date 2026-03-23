---
Document_ID: 14
Title: Environment Setup & Deployment Guide
Chinese_Name: 环境搭建与部署指南
Version: V1.0
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 14 环境搭建与部署指南 (Environment Setup & Deployment Guide)

> **核心目标**：指导运维人员在内网或生产环境中，从零开始完成全栈资产管理系统的部署与验证。

## 1. 基础环境要求
*   **操作系统**：Linux (CentOS 7+/Ubuntu 18.04+) 或 Windows Server 2016+
*   **Java**：JDK 1.8 (必须)
*   **数据库**：MySQL 8.0+
*   **搜索引擎**：Apache Solr 8.11 (推荐)
*   **前端容器**：Nginx 1.18+
*   **预览服务**：OnlyOffice Document Server (Docker 部署)

## 2. 数据库部署 (MySQL)
1.  **创建数据库**：
    ```sql
    CREATE DATABASE asset_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    ```
2.  **导入表结构与数据**：
    *   执行 `db_v2.sql` (表结构)
    *   执行 `init_data.sql` (初始数据)
3.  **配置权限**：确保应用服务器 IP 拥有访问权限。

## 3. 搜索引擎部署 (Solr)
1.  **启动容器**：
    ```bash
    docker run -d -p 8983:8983 --name asset-solr --restart=always solr:8.11
    ```
2.  **创建 Core**：
    ```bash
    docker exec -it asset-solr solr create_core -c file_search
    ```
3.  **配置 IK 分词器**：
    *   将 `ik-analyzer-8.5.0.jar` 拷贝至容器 `WEB-INF/lib`。
    *   修改 `managed-schema`，添加 `text_ik` 类型定义及业务字段映射（详见 `06_Solr_Schema_Configuration.md`）。
4.  **重启 Solr**：`docker restart asset-solr`。

## 4. 后端服务部署 (SpringBoot)
1.  **打包**：`mvn clean package` 生成 `java-asset-full-service-0.0.1-SNAPSHOT.jar`。
2.  **环境配置**：系统已内置 `application-prod.yml` 生产环境配置。
    *   **方式 A (推荐)**：启动时激活 `prod` 配置文件，并通过命令行参数覆盖敏感信息。
    *   **方式 B (外部配置)**：在 Jar 包同级目录创建 `application-prod.yml`，Spring Boot 会优先加载外部文件。
    ```yaml
    # 外部 application-prod.yml 示例
    spring:
      datasource:
        url: jdbc:mysql://<实际数据库IP>:3306/asset_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
        username: <用户名>
        password: <密码>
      data:
        solr:
          host: http://<实际SolrIP>:8983/solr
    file:
      upload-dir: /data/asset_files
      recycle-bin-dir: /data/asset_recycle_bin
    ```
3.  **启动**：使用 `--spring.profiles.active=prod` 激活生产配置。
    ```bash
    nohup java -jar java-asset-full-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > app.log 2>&1 &
    ```

## 5. 前端服务部署 (Vue + Nginx)
1.  **打包**：`npm run build` 生成 `dist/` 目录。
2.  **Nginx 配置**：
    ```nginx
    server {
        listen 80;
        server_name asset.bank.com;

        location / {
            root /var/www/asset-web/dist;
            index index.html;
            try_files $uri $uri/ /index.html; # 支持 History 模式
        }

        location /api {
            proxy_pass http://<BACKEND_IP>:8081;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
    ```

## 6. 预览服务部署 (OnlyOffice)
```bash
docker run -i -t -d -p 9000:80 \
    -e JWT_ENABLED=false \
    -e ALLOW_PRIVATE_IP_ADDRESS=true \
    --restart=always onlyoffice/documentserver
```

## 7. 生产环境复核清单
*   [ ] **安全**：修改 `auth.secret-key` 为随机强密钥。
*   [ ] **权限**：确保 `upload-dir` 目录对 Java 进程有读写权限。
*   [ ] **索引**：登录系统后，手动触发一次“全量重建索引”。
*   [ ] **备份**：配置 MySQL 定时备份脚本（`mysqldump`）。
*   [ ] **监控**：配置 SpringBoot Actuator 监控端点。

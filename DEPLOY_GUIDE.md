# 后端服务内网部署方案 (Offline Deployment Guide)

本方案旨在指导您如何在完全离线的内网环境中，从零开始部署 `java-asset-full-service` 后端服务。

## 1. 环境准备
在开始部署前，请确保目标服务器（Linux 或 Windows）已安装以下基础环境：

- **Java Development Kit (JDK)**: `1.8` 或更高版本。
- **MySQL**: `8.0` 或更高版本。
- **Apache Solr**: 任意稳定版本（推荐 `8.x`）。
- **(可选) OnlyOffice Document Server**: 通过 Docker 部署。

## 2. 部署步骤

### 步骤 1：数据库准备
1.  **创建数据库**:
    在您的 MySQL 服务器上创建一个新的数据库。
    ```sql
    CREATE DATABASE asset_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    ```
2.  **导入表结构**:
    使用 MySQL 客户端，执行项目根目录下的 `db_v2.sql` 文件，创建所有必需的表。
    ```bash
    mysql -u your_user -p asset_db < db_v2.sql
    ```
3.  **导入初始数据**:
    执行 `init_data.sql` 文件，导入系统的基础数据（如用户、专区根目录等）。
    ```bash
    mysql -u your_user -p asset_db < init_data.sql
    ```

### 步骤 2：Solr 服务准备
1.  **安装 Solr**:
    请参照 `SOLR_DEPLOY_GUIDE.md` 完成 Solr 的安装和 Core 的创建。核心步骤是确保一个名为 `file_search` 的 Core 正在运行。
2.  **验证 Solr**:
    在浏览器中访问 `http://<Solr服务器IP>:8983/solr`，并从左侧的 Core Selector 中选择 `file_search`，确保可以进入查询界面。

### 步骤 3：(可选) OnlyOffice 服务准备
如果您需要 Office 文档的在线预览功能，请在内网的一台服务器上通过 Docker 部署 OnlyOffice。
```bash
docker run -i -t -d -p 9000:80 \
    -e JWT_ENABLED=false \
    -e ALLOW_PRIVATE_IP_ADDRESS=true \
    --restart=always onlyoffice/documentserver
```
- **`9000:80`**: 将容器的 80 端口映射到宿主机的 9000 端口。
- **`JWT_ENABLED=false`**: 禁用安全令牌，简化内网部署。
- **`ALLOW_PRIVATE_IP_ADDRESS=true`**: 允许容器访问私有 IP 地址（**关键**），以便回访后端服务获取文件。

### 步骤 4：打包后端应用
1.  **获取代码**:
    将 `java-asset-full-service` 项目的完整代码拷贝到一台可以访问外网 Maven 仓库的机器上。
2.  **执行打包**:
    在项目根目录下打开终端，执行 Maven 打包命令。
    ```bash
    mvn clean package
    ```
    命令执行成功后，会在 `target/` 目录下生成一个名为 `java-asset-full-service-0.0.1-SNAPSHOT.jar` 的文件。

### 步骤 5：配置与部署
1.  **上传 Jar 包**:
    将上一步生成的 `.jar` 文件上传到您的内网应用服务器。
2.  **创建配置文件**:
    在 `.jar` 文件所在的同级目录下，创建一个 `application.yml` 文件。这是为了覆盖 Jar 包内部的配置，方便管理。
3.  **编辑配置文件**:
    将以下内容复制到新建的 `application.yml` 中，并根据您的内网环境修改**所有**标记为 `<...>` 的部分。
    ```yaml
    server:
      port: 8081

    spring:
      application:
        name: asset-service
      datasource:
        driver-class-name: com.mysql.cj.jdbc.Driver
        url: jdbc:mysql://<您的MySQL服务器IP>:3306/asset_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai
        username: <您的MySQL用户名>
        password: <您的MySQL密码>
      data:
        solr:
          host: http://<您的Solr服务器IP>:8983/solr

    file:
      # 重要：请确保此目录存在且应用有读写权限
      upload-dir: <文件存储的绝对路径，例如 /data/asset_files 或 D:/data/asset_files>
    ```
4.  **创建存储目录**:
    在服务器上创建您在 `file.upload-dir` 中指定的目录，并确保运行服务的用户拥有该目录的读写权限。

### 步骤 6：启动服务
在应用服务器的终端中，进入 `.jar` 和 `application.yml` 所在的目录，执行以下命令启动服务：
```bash
java -jar java-asset-full-service-0.0.1-SNAPSHOT.jar
```
服务启动后，您应该能看到 Spring Boot 的启动日志。

## 4. 验证
- **API 访问**: 尝试在浏览器或使用 `curl` 访问 `http://<应用服务器IP>:8081/search`，如果能收到 JSON 响应，则表示服务已成功启动。
- **日志检查**: 查看服务启动日志，确认没有数据库连接或 Solr 连接的报错信息。

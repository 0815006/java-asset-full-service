# Solr 搜索引擎 Docker 部署与中文分词配置指南

本文档详细介绍了如何在本地或服务器上使用 Docker 部署 Solr 搜索引擎，并为其配置 IK 中文分词器，以支持本项目的全文检索功能。

## 1. 环境准备
确保您的服务器或本地已安装 Docker。

## 2. 部署步骤

### 步骤 1：拉取并启动 Solr 容器
本项目基于 Spring Boot 2.x，推荐使用 **Solr 8.11** 版本以保证最佳兼容性。

执行以下命令启动 Solr 容器：
```bash
docker run -d -p 8983:8983 --name asset-solr --restart=always solr:8.11
```
启动后，您可以通过浏览器访问 `http://您的IP:8983/solr` 查看 Solr 管理界面。

### 步骤 2：创建核心 (Core)
根据后端代码逻辑，本项目使用的核心名称必须为 `file_search`。

进入容器内部创建核心：
```bash
docker exec -it asset-solr solr create_core -c file_search
```

### 步骤 3：安装 IK 中文分词器
为了让文件名（`name`）和文件内容（`text`）支持精准的中文搜索，必须安装 IK 分词器。

1. **下载 IK 分词器插件**
   由于 Solr 8.x 版本的 IK 分词器插件较多，推荐使用开源社区维护的版本（例如 `ik-analyzer-solr`）。
   您可以从 GitHub 下载适用于 Solr 8.x 的 IK Analyzer jar 包（通常包含 `ik-analyzer-8.x.x.jar`）。

2. **将插件复制到容器中**
   假设您已经下载了 `ik-analyzer-8.5.0.jar`，将其复制到 Solr 容器的 lib 目录下：
   ```bash
   # 将 jar 包拷贝到容器的 WEB-INF/lib 目录
   docker cp ik-analyzer-8.5.0.jar asset-solr:/opt/solr-8.11.2/server/solr-webapp/webapp/WEB-INF/lib/
   ```
   *(注意：`/opt/solr-8.11.2/` 路径中的版本号请根据实际启动的 Solr 版本进行调整，可以通过 `docker exec -it asset-solr ls /opt/` 查看)*

3. **复制配置文件（可选但推荐）**
   如果您需要自定义词库，还需要将 `IKAnalyzer.cfg.xml`、`ext.dic`、`stopword.dic` 复制到容器的 `classes` 目录下：
   ```bash
   # 在容器内创建 classes 目录
   docker exec -it asset-solr mkdir -p /opt/solr-8.11.2/server/solr-webapp/webapp/WEB-INF/classes
   # 拷贝配置文件
   docker cp IKAnalyzer.cfg.xml asset-solr:/opt/solr-8.11.2/server/solr-webapp/webapp/WEB-INF/classes/
   docker cp ext.dic asset-solr:/opt/solr-8.11.2/server/solr-webapp/webapp/WEB-INF/classes/
   docker cp stopword.dic asset-solr:/opt/solr-8.11.2/server/solr-webapp/webapp/WEB-INF/classes/
   ```

### 步骤 4：配置 Schema 字段
我们需要告诉 Solr 哪些字段使用中文分词。

1. **进入容器并编辑 `managed-schema` 文件**
   ```bash
   docker exec -it -u root asset-solr /bin/bash
   # 安装 vim（如果容器内没有）
   apt-get update && apt-get install -y vim
   # 编辑 file_search 核心的 schema 文件
   vim /var/solr/data/file_search/conf/managed-schema
   ```

2. **添加 IK 分词器类型定义**
   在 `<schema>` 标签内部（通常在其他 `<fieldType>` 定义的下方），添加以下内容：
   ```xml
   <!-- IK Analyzer 中文分词器 -->
   <fieldType name="text_ik" class="solr.TextField">
     <analyzer type="index">
         <tokenizer class="org.wltea.analyzer.lucene.IKTokenizerFactory" useSmart="false" conf="ik.conf"/>
         <filter class="solr.LowerCaseFilterFactory"/>
     </analyzer>
     <analyzer type="query">
         <tokenizer class="org.wltea.analyzer.lucene.IKTokenizerFactory" useSmart="true" conf="ik.conf"/>
         <filter class="solr.LowerCaseFilterFactory"/>
     </analyzer>
   </fieldType>
   ```

3. **定义业务字段**
   继续在 `managed-schema` 中找到 `<field ...>` 定义区域，添加或修改以下字段（如果已存在 `id` 等字段则无需重复添加，只需确保类型正确）：
   ```xml
   <!-- 唯一主键 (通常已默认存在) -->
   <!-- <field name="id" type="string" indexed="true" stored="true" required="true" multiValued="false" /> -->
   
   <!-- 文件名，使用中文分词 -->
   <field name="name" type="text_ik" indexed="true" stored="true" />
   
   <!-- 文件全文内容，使用中文分词 -->
   <field name="text" type="text_ik" indexed="true" stored="true" multiValued="true" />
   
   <!-- 其他业务字段 -->
   <field name="product_id" type="plong" indexed="true" stored="true" />
   <field name="ext" type="string" indexed="true" stored="true" />
   <field name="tree_path" type="string" indexed="true" stored="true" />
   <field name="zone_type" type="string" indexed="true" stored="true" />
   ```
   保存并退出 vim (`:wq`)。

### 步骤 5：重启 Solr 容器
配置完成后，必须重启容器使分词器和 Schema 生效：
```bash
docker restart asset-solr
```

### 步骤 6：验证分词器
1. 打开浏览器访问：`http://您的IP:8983/solr/#/file_search/analysis`
2. 在 "Field Value (Index)" 框中输入一段中文，例如：“测试技术与工艺专区”。
3. 在 "Analyse Fieldname / FieldType" 下拉框中选择 `text_ik`。
4. 点击 "Analyse Values" 按钮。
5. 如果下方能正确显示被切分出的中文词语，说明 IK 分词器配置成功！

## 3. Spring Boot 后端配置
确保后端项目 `src/main/resources/application.yml` 中的 Solr 地址指向您刚刚部署的服务器：

```yaml
spring:
  data:
    solr:
      host: http://localhost:8983/solr # 如果部署在其他机器，请修改为实际 IP
```

## 4. 初始化数据索引
部署并配置完成后，Solr 中目前是空的。
您需要登录系统前端，进入相关管理页面，点击**“重建索引”**按钮（或调用对应的后端接口 `/api/search/rebuild`），后端会遍历数据库中所有已上传的文件，提取文本内容并推送到 Solr 中建立索引。

至此，Solr 搜索引擎部署完毕，全文检索功能即可正常使用！
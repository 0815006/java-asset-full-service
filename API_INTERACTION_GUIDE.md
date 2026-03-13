# 后端数据交互说明 (API Interaction Guide)

## 0. 接口清单 (API Quick List)

### AssetFileController (`/assets`)
- `GET /tree`: 获取文件目录树
- `POST /upload`: 上传新文件
- `POST /{id}/update`: 更新文件版本
- `GET /{id}/view`: 获取文件流 (用于预览或下载)
- `POST /download`: 批量打包下载
- `POST /sync-extra`: 一键入库 (同步物理文件到数据库)
- `GET /sync-progress`: 获取一键入库进度
- `POST /folder`: 创建文件夹
- `PUT /{id}/rename`: 重命名文件/文件夹
- `DELETE /{id}`: 删除文件/文件夹
- `PUT /{id}/move`: 移动文件/文件夹
- `GET /{id}/details`: 获取文件完整信息

### SearchController (`/search`)
- `GET /`: 执行全文检索
- `GET /health-check`: 索引健康检查
- `POST /reindex/{id}`: 重建单个索引
- `DELETE /index/{solrId}`: 清理单个多余索引
- `POST /rebuild-all/start`: 开始全量重建索引
- `GET /rebuild-all/progress`: 获取全量重建进度

### ProductController (`/products`)
- `GET /`: 获取产品列表
- `POST /favorite`: 添加/取消产品收藏

### LoginController (`/`)
- `POST /login`: 用户登录

---

本指南详细说明了后端服务提供的核心 API 接口、其业务逻辑及与数据库的交互方式。

## 1. `AssetFileController` - 核心资产文件管理

### `GET /assets/tree`
- **功能**: 获取指定节点下的文件和文件夹列表（懒加载）。
- **参数**: `product_id`, `parent_id`。
- **后台逻辑**:
  1.  查询 `asset_file` 表，条件为 `product_id = ? AND parent_id = ? AND is_latest = 1`。
  2.  对查询出的每个文件夹节点，额外执行一次 `COUNT` 查询，判断其是否有子节点，用于设置前端的 `hasChildren` 属性。
- **数据库操作**: `SELECT` from `asset_file`。

### `POST /assets/upload`
- **功能**: 上传新文件。
- **参数**: `file`, `product_id`, `parent_id`, `zone_type` (可选)。
- **后台逻辑**:
  1.  检查同目录下是否存在同名文件，如果存在，则将旧记录的 `is_latest` 设为 `0` (版本覆盖)。
  2.  创建一条新的 `asset_file` 记录，`is_latest` 为 `1`，`version_no` 在旧版本基础上 +1。
  3.  根据 `parent_id` 构建新文件的 `tree_path`。
  4.  将上传的文件保存到由 `getPhysicalPath` 方法决定的物理路径。
  5.  调用 `SearchService.index()` 将新文件信息同步到 Solr。
- **数据库操作**: `UPDATE` (旧版本) 和 `INSERT` (新版本) into `asset_file`。

### `POST /assets/{id}/update`
- **功能**: 更新（覆盖）一个已存在的文件。
- **参数**: `id` (路径参数), `file` (文件体)。
- **后台逻辑**:
  1.  将 ID 对应的旧 `asset_file` 记录的 `is_latest` 设为 `0`。
  2.  创建一条新的 `asset_file` 记录，继承大部分旧记录的元数据，但更新 `file_size`, `version_no` 等信息。
  3.  用新文件的内容覆盖 `local_path` 指向的旧物理文件。
  4.  调用 `SearchService.index()` 更新 Solr 索引。
- **数据库操作**: `UPDATE` 和 `INSERT` into `asset_file`。

### `GET /assets/{id}/view`
- **功能**: 文件预览或单文件下载。
- **参数**: `id`, `download` (可选, `true` 时为下载)。
- **后台逻辑**:
  1.  根据 `id` 查询 `asset_file` 表获取 `local_path`。
  2.  从物理路径读取文件，以文件流的形式返回。
  3.  如果 `download=true`，则在响应头中加入 `Content-Disposition: attachment` 以触发浏览器下载。
- **数据库操作**: `SELECT` from `asset_file`。

### `POST /assets/download`
- **功能**: 批量打包下载文件。
- **后台逻辑**:
  1.  接收文件/文件夹 ID 列表。
  2.  如果 ID 是文件夹，则递归查询 `asset_file` 表（使用 `tree_path LIKE ...`）获取其下所有子文件。
  3.  将所有待下载的文件物理路径收集起来。
  4.  使用 `ZipOutputStream` 将文件打包成 ZIP 流返回。
- **数据库操作**: `SELECT` from `asset_file`。

### `POST /assets/sync-extra`
- **功能**: 一键入库，将物理目录中存在但数据库中不存在的文件同步到数据库。
- **后台逻辑**:
  1.  设置并发锁，防止重复执行。
  2.  递归扫描 `file.upload-dir` 下指定专区的物理目录。
  3.  与数据库中的 `asset_file` 记录进行比对。
  4.  如果发现物理文件在数据库中不存在，则执行 `INSERT` 操作，并为其创建索引。
- **数据库操作**: `SELECT` 和 `INSERT` into `asset_file`。

---

## 2. `SearchController` - 全文检索与索引管理

### `GET /search`
- **功能**: 执行全文检索。
- **参数**: `keyword`, `zoneType`, `productId` (可选)。
- **后台逻辑**:
  1.  调用 `SearchService.search()`，优先尝试从 Solr 查询。
  2.  **Solr 查询失败降级**: 如果 Solr 连接异常，会自动切换到 `databaseSearchFallback` 方法，执行数据库的 `LIKE` 模糊查询。
  3.  **产品名称回填**: 对 `zone_type` 为 `product` 的结果，会根据 `product_id` 批量查询 `product` 表，将产品名称回填到 `zone_name` 字段。
- **数据库操作**: `SELECT` from `asset_file` (降级时), `SELECT` from `product`。

### `GET /search/health-check`
- **功能**: 索引健康检查。
- **后台逻辑**:
  1.  查询数据库中所有最新版本的文件 (`SELECT * FROM asset_file WHERE is_latest = 1 AND node_type = 2`)。
  2.  调用 `SearchService.getAllIndexedDocuments()` 从 Solr 获取所有文档。
  3.  将两个列表返回给前端，由前端进行比对和展示。
- **数据库操作**: `SELECT` from `asset_file`。

### `POST /search/reindex/{id}`
- **功能**: 为单个缺失的文件重建索引。
- **后台逻辑**:
  1.  根据 `id` 查询 `asset_file` 表获取文件完整信息。
  2.  调用 `SearchService.index()` 将其写入 Solr。
- **数据库操作**: `SELECT` from `asset_file`。

### `DELETE /search/index/{solrId}`
- **功能**: 清理 Solr 中多余的索引。
- **后台逻辑**:
  1.  调用 `SearchService.deleteBySolrId()` 直接删除 Solr 中指定 ID 的文档。
- **数据库操作**: 无。

---

## 3. 其他控制器

### `ProductController`
- **`GET /products`**: 获取所有产品列表。
  - **数据库**: `SELECT * FROM product`。
- **`POST /products/favorite`**: 添加/取消产品收藏。
  - **数据库**: `INSERT` 或 `DELETE` from `user_favorite_product`。

### `LoginController`
- **`POST /login`**: 用户登录。
  - **数据库**: `SELECT * FROM user WHERE username = ?`。

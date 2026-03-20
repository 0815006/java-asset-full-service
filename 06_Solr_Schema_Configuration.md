---
Document_ID: 06
Title: Solr Schema Configuration
Chinese_Name: Solr 索引配置规范
Version: V1.0
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 06 Solr 索引配置规范 (Solr Schema Configuration)

> **核心目标**：实现海量非结构化文档的毫秒级全文检索，并提供精准的关键字高亮展示。

## 1. 索引库定义
*   **Core 名称**：`file_search`
*   **分词器**：IK Analyzer (针对中文优化)
*   **数据来源**：MySQL `asset_file` 表元数据 + Apache Tika 提取的文档全文。

## 2. 字段定义 (Field Definitions)

| 字段名 | 类型 | 索引 | 存储 | 多值 | 备注 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `id` | string | 是 | 是 | 否 | 唯一标识 (对应数据库 `id`) |
| `name` | text_ik | 是 | 是 | 否 | 文件名 (支持分词搜索) |
| `product_id` | plong | 是 | 是 | 否 | 所属产品 ID |
| `ext` | string | 是 | 是 | 否 | 文件扩展名 |
| `tree_path` | string | 是 | 是 | 否 | 树形路径 |
| `zone_type` | string | 是 | 是 | 否 | 区域类型 (tech/mgmt/product) |
| `text` | text_ik | 是 | 是 | 否 | 文档全文内容 (由 Tika 提取) |
| `_version_` | plong | 是 | 是 | 否 | Solr 内部版本控制 |

## 3. 分词策略 (Tokenization)
*   **中文分词**：采用 `IKAnalyzer`，配置 `useSmart=true` 以获得更准确的切分结果。
*   **文件名搜索**：`name` 字段同时支持前缀匹配和分词匹配，提升搜索灵活性。
*   **全文搜索**：`text` 字段存储 Tika 提取的文本，经过停用词过滤和分词处理。

## 4. 高亮配置 (Highlighting)
为了提升用户体验，搜索结果需对关键字进行视觉增强：

*   **前置标签**：`<em style='color:red'>`
*   **后置标签**：`</em>`
*   **摘要长度 (Fragsize)**：100 字符
*   **摘要数量 (Snippets)**：1 个
*   **高亮字段**：`text` (优先展示全文中的匹配片段)

## 5. 查询优化逻辑
1.  **过滤查询 (Filter Query)**：
    *   专区过滤：使用 `fq=zone_type:tech` 缩小搜索范围，不影响相关度评分。
    *   产品过滤：使用 `fq=product_id:123`。
2.  **权重配置 (Boosting)**：
    *   文件名权重：`name^2.0` (文件名匹配的优先级高于全文匹配)。
3.  **性能保障**：
    *   **Tika 限制**：单文件内容提取上限设为 10MB，防止超大文件导致内存溢出。
    *   **空白清理**：在索引前通过正则 `\\s+` 清理多余空白字符，减小索引体积。

## 6. 同步机制
*   **实时同步**：在 `AssetFileController` 的 `upload` 和 `update` 接口中，文件保存成功后立即调用 `searchService.index()`。
*   **异步重建**：提供 `startRebuildAll` 接口，支持在后台线程中全量重新构建索引，并提供进度查询。

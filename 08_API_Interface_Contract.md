---
Document_ID: 08
Title: API Interface Contract
Chinese_Name: 接口契约规约
Version: V1.0
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 08 接口契约规约 (API Interface Contract)

> **核心目标**：定义前后端通信的标准协议，确保数据交换的准确性与安全性。

## 1. 通用规范
*   **基础路径**：`/api`
*   **数据格式**：`application/json`
*   **认证方式**：Header 携带 `Authorization: Bearer <token>`
*   **统一响应格式**：
    ```json
    {
      "code": 200,
      "message": "操作成功",
      "data": { ... }
    }
    ```

## 2. 核心接口定义

### 2.1 认证模块
*   **用户登录**：`POST /api/login`
    *   **入参**：`{ "username": "...", "password": "..." }`
    *   **出参**：`{ "token": "...", "user": { ... } }`

### 2.2 资产浏览模块
*   **获取资产树**：`GET /api/asset/tree`
    *   **参数**：`product_id`, `parent_id`, `userId`
    *   **说明**：支持懒加载，返回当前层级的文件夹与文件。
*   **资产详情**：`GET /api/asset/{id}/details`
    *   **说明**：返回资产元数据、New 标状态、收藏状态。
*   **预览/下载**：`GET /api/asset/{id}/view`
    *   **参数**：`download=true/false`
    *   **说明**：直接返回文件流，自动识别 Content-Type。

### 2.3 搜索模块
*   **全文检索**：`GET /api/search`
    *   **参数**：`keyword`, `zoneType`, `productId`, `page`, `size`
    *   **说明**：返回包含高亮片段的搜索结果。
*   **热门搜索词**：`GET /api/search/hot-keywords`

### 2.4 推荐与排行模块
*   **最近访问**：`GET /api/asset/recent-access`
*   **我的收藏**：`GET /api/asset/my-stars`
*   **最新更新**：`GET /api/asset/latest-updates`
*   **核心资产**：`GET /api/asset/curated/{productId}`
*   **使用排行**：`GET /api/asset/product-use-top/{productId}`

### 2.5 资产管理模块
*   **上传资产**：`POST /api/asset/upload` (Multipart)
*   **创建文件夹**：`POST /api/asset/folder`
*   **逻辑删除**：`DELETE /api/asset/{id}`
*   **收藏/取消收藏**：`POST /api/asset/star/{fileId}`, `DELETE /api/asset/star/{fileId}`
*   **置顶/取消置顶**：`POST /api/asset/star/{fileId}/pin?pin=true`

## 3. 错误码定义
详见 `09_Global_Exception_Codes.md`。常见错误码：
*   `200`: 成功
*   `401`: 未授权（Token 失效或缺失）
*   `403`: 权限不足
*   `500`: 服务器内部错误

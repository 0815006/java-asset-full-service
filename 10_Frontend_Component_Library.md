---
Document_ID: 10
Title: Frontend Component Library
Chinese_Name: 前端组件库规范
Version: V1.0
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 10 前端组件库规范 (Frontend Component Library)

> **核心目标**：确保 Vue 2 + Element UI 的组件风格统一，实现“深色极客风”的视觉效果。

## 1. 技术栈
*   **框架**：Vue 2.x (Options API)
*   **UI 库**：Element UI (Vue 2 版本)
*   **样式**：SCSS + Scoped CSS
*   **图标**：Element UI 内置图标 + 自定义 SVG

## 2. 核心组件拆分

### 2.1 资产展示组件 (`AssetItem.vue`)
*   **功能**：单个资产（文件/文件夹）的卡片式展示。
*   **特性**：
    *   根据后缀显示不同图标（PDF, Word, Excel, Image 等）。
    *   显示 "New" 标、收藏状态、置顶状态。
    *   右键菜单支持：重命名、移动、删除、收藏、置顶。

### 2.2 预览组件 (`SuperPreview.vue`)
*   **功能**：全能预览容器。
*   **子组件**：
    *   `ImageViewer.vue`：图片缩放、旋转。
    *   `PdfViewer.vue`：基于 PDF.js 的在线阅读。
    *   `OfficeViewer.vue`：集成 OnlyOffice 的文档预览。
    *   `TextViewer.vue`：代码/文本高亮展示。

### 2.3 列表容器组件
*   **`RecentAccessList.vue`**：个人最近访问列表。
*   **`MyStarList.vue`**：个人收藏置顶列表。
*   **`LatestUpdatesList.vue`**：全行最新更新列表。
*   **`ProductCuratedAssetList.vue`**：产品精选资产列表。

### 2.4 交互对话框
*   **`SearchResultDialog.vue`**：全局搜索结果展示，支持关键字高亮。
*   **`RecycleBinDialog.vue`**：回收站管理，支持恢复与彻底删除。
*   **`UpdateFileDialog.vue`**：版本更新上传对话框。

## 3. 全局样式变量 (深色极客风)
```scss
// 核心配色
$bg-color: #0b0e14;        // 深色背景
$card-bg: rgba(255, 255, 255, 0.05); // 玻璃拟态背景
$primary-color: #409EFF;   // 品牌蓝
$text-main: #e0e0e0;       // 主文字
$text-secondary: #909399;  // 次要文字
$border-color: #2d2f33;    // 边框颜色

// 玻璃拟态效果
.glass-card {
  background: $card-bg;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}
```

## 4. 组件开发规范
1.  **命名**：组件文件名采用 PascalCase (如 `AssetItem.vue`)。
2.  **Props**：必须定义类型和默认值。
3.  **Events**：使用 `this.$emit` 进行父子组件通信。
4.  **Loading**：耗时操作（如列表加载、文件上传）必须使用 `v-loading` 指令。
5.  **Message**：所有接口报错必须通过 `this.$message.error` 统一弹出。

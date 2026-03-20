---
Document_ID: 11
Title: Frontend Route Map
Chinese_Name: 前端路由映射表
Version: V1.0
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 11 前端路由映射表 (Frontend Route Map)

> **核心目标**：定义 Vue-Router 的路由配置及动态权限加载逻辑。

## 1. 路由配置 (Vue-Router)

| 路径 | 名称 | 组件 | 权限要求 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| `/login` | `Login` | `Login.vue` | 无 | 登录页面 |
| `/` | `Home` | `Home.vue` | 需登录 | 首页仪表盘 (Dashboard) |
| `/product/:id` | `ProductDetail` | `ProductDetail.vue` | 需登录 | 产品详情页 (资产树展示) |

## 2. 路由守卫 (Navigation Guards)
系统采用全局前置守卫进行身份校验：

*   **逻辑**：
    1.  检查目标路径是否为 `/login`。
    2.  若不是 `/login` 且本地存储中没有 `token`，则强制跳转至 `/login`。
    3.  若已登录，则允许通过。
*   **代码实现**：
    ```javascript
    router.beforeEach((to, from, next) => {
      const token = localStorage.getItem('token')
      if (to.path !== '/login' && !token) {
        next('/login')
      } else {
        next()
      }
    })
    ```

## 3. 动态权限路由 (未来扩展)
目前系统采用全量路由加载。未来可根据 `User.roleType` 实现动态路由注入：

*   **管理员 (roleType=1)**：可见“系统管理”、“全行资产审计”等路由。
*   **普通用户 (roleType=3)**：仅可见“首页”及“产品详情”。

## 4. 页面布局结构
*   **根容器**：`App.vue`
*   **公共头部**：`MainHeader.vue` (包含搜索框、个人中心、消息提醒)。
*   **内容区**：`<router-view>` 动态渲染。

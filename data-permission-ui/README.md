# data-permission-ui

数据权限规则管理页面，目标是直接复制进 RuoYi-Vue（Vue 2 + Element UI）项目使用。页面不依赖 RuoYi 的全局 `pagination`、`right-toolbar`、`$modal` 等组件/插件，只使用 Element UI，因此任何 Vue 2 + Element UI 后台都可以接入。

## 目录

```
src/api/system/dataPermission.js              # 接口封装
src/views/system/dataPermission/index.vue     # 规则列表
src/views/system/dataPermission/components/
  RuleDialog.vue                              # 新增/编辑规则
  RoleRulesDialog.vue                         # 角色规则分配
demo/                                         # 本地运行用的 Mock 环境和入口
```

## 接入 RuoYi

1. 后端开启管理接口：

   ```yaml
   data-permission:
     admin:
       enabled: true
   ```

   并确认管理接口已被 Spring Security / 网关保护。页面本身不做权限判断。

2. 复制文件到前端工程：

   - `src/api/system/dataPermission.js` → `src/api/system/dataPermission.js`
   - `src/views/system/dataPermission/` → `src/views/system/dataPermission/`

3. 菜单由 RuoYi 的「系统管理 → 菜单管理」维护，新增菜单时：

   - 路由地址：`dataPermission`
   - 组件路径：`system/dataPermission/index`
   - 菜单类型：菜单
   - 权限标识：按项目规范填写，例如 `system:dataPermission:list`

   如果使用静态路由，可在 `src/router/index.js` 中加：

   ```js
   {
     path: '/system/dataPermission',
     component: Layout,
     children: [{
       path: 'index',
       component: () => import('@/views/system/dataPermission/index'),
       name: 'DataPermission',
       meta: { title: '数据权限', icon: 'lock' }
     }]
   }
   ```

4. 角色 ID 使用 `sys_role.role_id`，角色分配弹窗里手动输入即可。

## 页面能力

- 按应用点、规则名称筛选规则
- 新增/编辑简单规则：字段、操作符、值（支持 `#{deptId}`、`IN`、`LIKE`、`IS NULL`）
- 新增/编辑复杂规则：组、子组、明细排序、逻辑符、左右值类型
- 启用/停用规则
- 删除规则
- 给角色绑定规则（覆盖式保存）

## 本地演示

```bash
cd data-permission-ui
npm install
npm run dev
```

打开 `http://127.0.0.1:5173`。演示环境通过 Vite alias 把 `@/utils/request` 指向 `demo/utils/request.js`，数据保存在内存里；`src/` 下的页面和接口文件与接入 RuoYi 时完全一致。

```bash
npm run build
```

构建产物在 `dist/`，仅用于验证编译，不需要部署。

## 后端接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/data-permission/admin/rules?appPoint=&ruleName=` | 规则列表 |
| GET | `/data-permission/admin/rules/{id}` | 规则详情（含复杂明细） |
| POST | `/data-permission/admin/rules` | 新增/更新，返回 `{id}` |
| DELETE | `/data-permission/admin/rules/{ids}` | 删除，多个 id 用逗号分隔 |
| GET | `/data-permission/admin/roles/{roleId}/rules` | 角色已绑定规则 id |
| PUT | `/data-permission/admin/roles/{roleId}/rules` | 覆盖角色规则，body `{ruleIds}` |

## 兼容性

- 页面兼容 Vue 2.6+ 与 Element UI 2.15（RuoYi-Vue 2.6.12 已验证页面语法）；本地 demo 的构建工具链使用 Vue 2.7
- 接口前缀与后端 `data-permission.admin.path` 保持一致，默认 `/data-permission/admin`
- 若接入 RuoYi-Vue-Plus（Vue 3 + Element Plus），直接使用 `feature/vue3-ui` 分支的 [data-permission-ui-vue3](https://github.com/ty-ux/data-permission/tree/feature/vue3-ui/data-permission-ui-vue3)，无需手动迁移
- 页面不做分页请求，列表接口返回全部匹配规则后在浏览器端分页；规则数量较大时建议给后端接口加分页参数

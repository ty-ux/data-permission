# 参与贡献

感谢你愿意改进 data-permission。本文说明提交 Issue 和 Pull Request 的规则；开始之前，请先阅读：

- [README](README.md)：项目能力、分支与产物、快速开始
- [接入指南](docs/integration.md)：RuoYi / Spring Security / 定时任务 / 灰度上线
- [规则语义与 SQL 改写](docs/rule-semantics.md)

参与讨论即表示同意保持友善、对事不对人。维护者会删除广告、人身攻击或与项目无关的内容。

## 先搜索，再提交

- 提交前先搜索 [现有 Issues](https://github.com/ty-ux/data-permission/issues) 和已合并的 PR，避免重复。
- 使用问题、接入疑问、方案讨论可以使用「使用问题」模板提交。
- **安全漏洞不要公开提交**，请按 [SECURITY.md](SECURITY.md) 私密报告。
- 会影响 SQL 改写、默认策略或公共 API 的改动，请先开 Issue 说明动机和方案，达成一致后再写代码。

## Issue 规则

一个 Issue 只讨论一个问题，不要把多个互不相关的问题混在一起。

### Bug 报告

使用「Bug 报告」模板，必须提供：

1. **分支与版本**：`main` 或 `feature/*`，release 版本号或 commit hash。
2. **环境**：JDK、Spring Boot、MyBatis、MyBatis-Plus、PageHelper 版本，以及是否运行在 RuoYi。
3. **最小复现**：表结构、Mapper 注解、规则配置（`sys_permission_rule`、`sys_permission_rule_item`、`sys_role_permission_rule`）和触发查询。
4. **期望行为与实际行为**：查询应该返回什么，实际返回或抛出了什么。
5. **日志或改写后的 SQL**：把 `data-permission.mode` 设为 `LOG_ONLY` 后贴出实际执行的 SQL；异常请附完整堆栈。

请勿粘贴真实业务数据、账号、密钥或内网地址；确需粘贴数据时请先脱敏。缺少复现信息的 Issue 会被要求补充，超过 30 天没有回应的可能被关闭。

### 功能建议

先描述**使用场景和要解决的问题**，再给方案。请说明：

- 期望行为，以及它与现有[规则语义](docs/rule-semantics.md)的关系；
- 是否影响默认拒绝（fail-closed）、分页 count、子查询或 CTE 改写；
- 你考虑过的替代方案。

### 标题与标签

- 模板会自动添加标题前缀（`[Bug]` / `[Feature]` / `[Question]`）和标签，无需手动添加。
- 维护者可能补充 `needs-repro`、`needs-info`、`wontfix` 等标签。
- 本项目由个人维护，不承诺固定响应时间，请勿反复 `@` 维护者催办。

## Pull Request 规则

### 1. 选择目标分支

仓库按宿主兼容性维护多条长期分支，PR 必须提交到与修改内容对应的分支：

| 目标分支 | 适用场景 |
| --- | --- |
| `main` | 默认分支，Java 8 + 原生 MyBatis，Vue 2 |
| `feature/jdk17` | Java 17 字节码 |
| `feature/mybatis-plus` | Java 8 + MyBatis-Plus 支持 |
| `feature/jdk17-mybatis-plus` | Java 17 + MyBatis-Plus 支持 |
| `feature/vue3-ui` | Vue 3 + Element Plus 管理页面 |

同一修复如果影响多条分支，请在 PR 中说明，由维护者决定 cherry-pick 还是分别提交。不要在一个 PR 里同时修改多条分支的内容。

### 2. 一个 PR 只做一件事

- 不要混入无关重构、格式化或依赖升级，这类改动请单独提交。
- 修改公共 API 时说明兼容性影响。
- `data-permission-core` 保持不依赖 Spring；优先复用现有依赖，确需新增时在 PR 中说明理由。
- 不要提交 `target/`、`dist/`、`node_modules/` 等生成物。

### 3. 测试与构建

提交前在本地执行：

```bash
mvn -B -ntp verify
```

前端有改动时执行：

```bash
cd data-permission-ui
npm ci
npm run build
```

要求：

- 缺陷修复必须包含能复现问题的回归测试；安全相关修复还要覆盖 fail-closed（无匹配规则时追加 `1 = 0`）场景。
- SQL 改写相关改动要覆盖子查询、CTE、JOIN、分页 count 等已有测试场景，新行为补对应的 H2 测试。
- CI 必须全绿；未通过 CI 的 PR 不会被审查。

### 4. 提交信息

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```text
feat: 支持字段对字段比较
fix: 修复嵌套子查询漏加权限条件
docs: 补充定时任务接入示例
build: 跳过 sample 模块发布
```

常用类型：`feat`、`fix`、`docs`、`test`、`refactor`、`perf`、`build`、`chore`。标题中英文均可，但要说明“改了什么、为什么”。PR 采用 squash merge，PR 标题会成为最终提交信息，请按同样格式填写。

### 5. PR 描述

按模板填写，至少说明：

- 变更内容和动机；
- 关联 Issue（`Closes #123`）；
- 目标分支；
- 测试方式与执行命令；
- 文档是否同步更新。

行为或语义有变化时，同步更新：

- [README](README.md) 的兼容性与使用说明；
- [docs/rule-semantics.md](docs/rule-semantics.md) 的规则语义；
- [docs/integration.md](docs/integration.md) 的接入方式。

### 6. 审查与合并

- 至少一位维护者 approve，且 CI 通过后才能合并。
- 维护者使用 squash merge；合并后你的分支可以删除。
- 审查意见请在同一 PR 内回复或推送新提交，不要关闭后重开。
- 长期没有进展的 PR 可能被标记为 stale 后关闭，随时可以重新打开。

## 许可证

本项目使用 [MIT 许可证](LICENSE)。提交 PR 即表示你确认自己有权提交这些代码，并同意以 MIT 许可证授权。

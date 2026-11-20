# data-permission

可配置的行级数据权限引擎，面向 RuoYi / Spring Boot 2.x + MyBatis 项目。规则以「角色 → 规则 → 表/字段」的方式存在数据库里，运行时由 MyBatis 拦截器把权限条件写入 SQL，业务查询不需要改。

[![CI](https://github.com/ty-ux/data-permission/actions/workflows/ci.yml/badge.svg)](https://github.com/ty-ux/data-permission/actions/workflows/ci.yml)

> 当前分支：`main`（Java 8 + 原生 MyBatis）。其他兼容性分支见[分支与产物](#分支与产物)。

## 特性

- **按角色配置**：一个角色可绑定多条规则；同一张表的规则按 `priority` / `sort_order` 组合。
- **简单规则 + 复杂规则**：字段比较、动态取值、常量、字段对字段、`IN`、`LIKE`、`IS NULL`；复杂规则支持分组（组间 OR、子组间 AND）。
- **默认拒绝（fail-closed）**：有登录用户但没有匹配规则、或动态值解析失败时，追加 `1 = 0`，而不是放行全部数据。
- **灰度模式**：`LOG_ONLY` 只记录将要改写的 SQL，不实际过滤，适合上线前逐条核对。
- **分页一致**：拦截器位于 PageHelper 外层，权限条件会进入 count SQL，分页总数与列表一致。
- **宿主无侵入**：通过反射适配 Spring Security principal（RuoYi `LoginUser` 开箱可用），不依赖宿主实体类；自定义认证体系只需提供一个 `DataPermissionSubjectProvider`。
- **注解驱动**：只有标注 `@DataPermission` 的 Mapper 方法或 Mapper 接口才会被处理，未注解的查询保持原样。
- **依赖隔离**：JSQLParser 4.6 被 shade 到 `com.ty.dp.shaded.jsqlparser`，不会与宿主已有的 JSQLParser 版本冲突。
- **无 Web 依赖也能启动**：宿主没有 `spring-web` 时不会注册请求过滤器，仍可用于定时任务等非 Web 场景。
- **Java 8 字节码**，Spring Boot 2.7.x / MyBatis 3.5.x 验证通过。

## 分支与产物

仓库按兼容性拆成多条分支，按宿主环境选择一条即可；这些分支都还没有合并到 `main`。

| 分支 | Java | MyBatis | 管理页面 | 说明 |
| --- | --- | --- | --- | --- |
| `main` | 8 | 原生 MyBatis | Vue 2 | 最兼容的默认基线 |
| `feature/jdk17` | 17 | 原生 MyBatis | Vue 2 | 需要 Java 17 字节码时使用 |
| `feature/mybatis-plus` | 8 | 原生 MyBatis + MyBatis-Plus | Vue 2 | 一套产物覆盖两种 MyBatis |
| `feature/jdk17-mybatis-plus` | 17 | 原生 MyBatis + MyBatis-Plus | Vue 2 | Java 17 + MyBatis-Plus |
| `feature/vue3-ui` | 8 | 原生 MyBatis | Vue 2 + Vue 3 | 面向 RuoYi-Vue-Plus 的前端示例 |

`feature/mybatis-plus` 和 `feature/jdk17-mybatis-plus` 的**库模块本身不依赖 MyBatis-Plus**，MP 只出现在 sample 的测试依赖里。同一套 jar 同时适用于原生 MyBatis 和 MyBatis-Plus 宿主；Java 8 字节码可以在 JDK 8/17 运行，Java 17 字节码要求 JDK 17+。

## 模块与依赖

| 模块 | 作用 | 是否对外 |
| --- | --- | --- |
| `data-permission-core` | 规则模型、SQL 改写、Subject 抽象、SPI，不依赖 Spring | 传递依赖 |
| `data-permission-mybatis` | MyBatis 拦截器、`@DataPermission` / `@DataPermissionIgnore` | 传递依赖 |
| `data-permission-starter` | Spring Boot 自动装配、JDBC 规则仓库、缓存、管理 API、建表脚本 | **接入方唯一需要声明的依赖** |
| `data-permission-sample` | H2 示例与测试，可执行 Spring Boot 应用 | 否，仅用于演示和测试 |
| `data-permission-ui` | Vue 2 + Element UI 规则管理页面 | 否，前端源码 |
| `data-permission-ui-vue3` | Vue 3 + Element Plus 页面（`feature/vue3-ui` 分支） | 否，前端源码 |

接入方只声明 `data-permission-starter` 即可，`core` 和 `mybatis` 由 Maven 传递。`sample` 是可执行示例，不要作为宿主依赖；发布时也只需要三个库 jar。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.ty</groupId>
    <artifactId>data-permission-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

发布到 GitHub Packages 后，在 `pom.xml` 或 `settings.xml` 中配置仓库：

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/ty-ux/data-permission</url>
</repository>
```

### 2. 建表

执行 [`data-permission-starter/src/main/resources/db/schema-mysql.sql`](data-permission-starter/src/main/resources/db/schema-mysql.sql)，创建三张表：

- `sys_permission_rule`：规则
- `sys_permission_rule_item`：复杂规则明细
- `sys_role_permission_rule`：角色与规则的关系

表名可通过 `data-permission.table.*` 覆盖，列名固定。

### 3. 配置

```yaml
data-permission:
  enabled: true
  mode: ENFORCE              # OFF | LOG_ONLY | ENFORCE
  default-policy: DENY       # 有用户但无规则
  no-subject-policy: ALLOW   # 无登录上下文（定时任务等）
  admin-user-ids:
    - "1"
  cache-ttl-seconds: 300
  admin:
    enabled: false           # 管理接口默认关闭
```

Spring Security 在 classpath 上时，会自动使用 `SpringSecurityDataPermissionSubjectProvider` 适配当前登录人；否则使用空实现，由调用方通过 `DataPermissionContext.setSubject(...)` 设置。无 `spring-web` 的宿主不会注册请求过滤器，定时任务等场景用 `DataPermissionContext.runAs(...)` 或 `without(...)` 显式设置/跳过。

### 4. 注解 Mapper

```java
@Mapper
public interface ProjectMapper {

    @DataPermission(appPoint = "project")
    @Select("select id, name, dept_id, owner_no from project")
    List<Project> selectAll();

    @DataPermissionIgnore
    @Select("select count(*) from project")
    long countAll();
}
```

XML Mapper 同理：把注解放到对应的 Mapper 接口方法上，SQL 可以继续写在 XML 里。

MyBatis-Plus 的 `BaseMapper` 完整支持（6 参数 `Executor.query`、分页 count、缓存 key 重建）在 `feature/mybatis-plus` 和 `feature/jdk17-mybatis-plus` 分支；`main` 只覆盖原生 MyBatis 与 PageHelper。

### 5. 配置规则

```sql
-- 简单规则：本部门
insert into sys_permission_rule
    (id, rule_name, app_point, table_name, field_name, condition_type,
     condition_value, rule_mode, logic_operator, priority, enabled, is_del)
values
    ('r-dept', '本部门', 'project', 'project', 'dept_id', '=',
     '#{deptId}', 'SIMPLE', 'AND', 0, '1', '0');

insert into sys_role_permission_rule (id, role_id, rule_id, is_del)
values ('rr-dept', '2', 'r-dept', '0');
```

`#{deptId}` 会依次从当前用户、Mapper 方法参数中取值。规则语义详见 [docs/rule-semantics.md](docs/rule-semantics.md)。

## 管理页面与接口

`data-permission.admin.enabled=true` 后提供最小化管理 API（默认前缀 `/data-permission/admin`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/rules?appPoint=&ruleName=` | 规则列表 |
| GET | `/rules/{id}` | 规则详情（含复杂明细） |
| POST | `/rules` | 新增/更新规则，返回 `{id}` |
| DELETE | `/rules/{ids}` | 逻辑删除，多个 id 用逗号分隔 |
| GET | `/roles/{roleId}/rules` | 角色已绑定的规则 id |
| PUT | `/roles/{roleId}/rules` | 覆盖角色规则，body：`{"ruleIds":["r1"]}` |

接口本身不做鉴权，**启用前必须由宿主项目在网关或 Spring Security 中限制访问**。规则变更后管理 API 会失效当前实例的缓存；多实例部署依赖 `cache-ttl-seconds` 自动过期。

前端页面：

- [`data-permission-ui`](data-permission-ui/README.md)：Vue 2 + Element UI，适用于 RuoYi-Vue。
- [Vue 3 + Element Plus 页面](https://github.com/ty-ux/data-permission/tree/feature/vue3-ui/data-permission-ui-vue3)：适用于 RuoYi-Vue-Plus，位于 `feature/vue3-ui` 分支。

## 构建、测试与发布

```bash
mvn -B -ntp verify
```

示例模块使用 H2 内存库，39 个测试覆盖简单规则、复杂规则、超级管理员、无规则拒绝、`@DataPermissionIgnore`、PageHelper 分页计数、规则变更后的缓存刷新和 `LOG_ONLY` 模式。

CI/CD：

- `ci.yml`：push / PR 时在 Java 8 和 17 上执行后端构建与测试，同时执行 Vue 2 管理页面的 `npm run build`。
- `release.yml`：推送 `v*` 标签时，把版本改为标签版本、构建、发布到 GitHub Packages，并创建 GitHub Release 附带三个库 jar。

```bash
git tag v0.1.0
git push origin v0.1.0
```

## 兼容性

- Java 8+（字节码目标 8）；`feature/jdk17` / `feature/jdk17-mybatis-plus` 使用 Java 17 字节码，要求 JDK 17+。
- Spring Boot 2.7.x（`javax.servlet`），可直接运行在 JDK 17 的 RuoYi 宿主。
- MyBatis 3.5.x；PageHelper 5.3.2 集成测试通过，权限条件会进入 count SQL。
- MyBatis-Plus：完整支持在 `feature/mybatis-plus` / `feature/jdk17-mybatis-plus`，固定 3.5.5。3.5.6+ 依赖 JSQLParser 4.9，与 PageHelper 5.3.2 及 core 的 4.6 冲突。拦截器覆盖 6 参数 `Executor.query` 并按重写后的 SQL 重建缓存 key；`BaseMapper` 用接口级 `@DataPermission`，分页 count 同样带权限条件。
- 仅拦截 `SELECT`，且仅处理标注 `@DataPermission` 的 Mapper 方法或接口。
- 无 `spring-web` 的宿主可以启动，此时不注册请求过滤器，需要自行 `DataPermissionContext.setSubject` / `runAs` / `without`。
- 管理页面：Vue 2 + Element UI 2.15（RuoYi-Vue 技术栈），不依赖 RuoYi 全局组件；Vue 3 页面在 `feature/vue3-ui` 分支。

## 文档

- [接入指南（RuoYi / Spring Security / 定时任务 / 灰度上线）](docs/integration.md)
- [规则语义与 SQL 改写规则](docs/rule-semantics.md)
- [管理页面接入（Vue 2 + Element UI）](data-permission-ui/README.md)
- [管理页面接入（Vue 3 + Element Plus，`feature/vue3-ui` 分支）](https://github.com/ty-ux/data-permission/tree/feature/vue3-ui/data-permission-ui-vue3)

## License

[MIT](LICENSE)

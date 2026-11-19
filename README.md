# data-permission

可配置的行级数据权限引擎，面向 RuoYi / Spring Boot 2.x + MyBatis 项目。规则以「角色 → 规则 → 表/字段」的方式存在数据库里，运行时由 MyBatis 拦截器把权限条件写入 SQL，无需改动业务查询。

[![CI](https://github.com/ty-ux/data-permission/actions/workflows/ci.yml/badge.svg)](https://github.com/ty-ux/data-permission/actions/workflows/ci.yml)

## 特性

- **按角色配置**：一个角色可绑定多条规则；同一张表的规则按 `priority` / `sort_order` 组合。
- **简单规则 + 复杂规则**：字段比较、动态取值、常量、字段对字段、`IN`、`LIKE`、`IS NULL`；复杂规则支持分组（组间 OR、子组间 AND）。
- **默认拒绝（fail-closed）**：有登录用户但没有匹配规则、或动态值解析失败时，追加 `1 = 0`，而不是放行全部数据。
- **灰度模式**：`LOG_ONLY` 只记录将要改写的 SQL，不实际过滤，适合上线前逐条核对。
- **与 PageHelper 协作**：拦截器位于分页插件外层，权限条件会进入 count SQL，分页总数与列表一致。
- **宿主无侵入**：通过反射适配 Spring Security principal（RuoYi `LoginUser` 开箱可用），不依赖宿主实体类；自定义认证体系只需提供一个 `DataPermissionSubjectProvider`。
- **注解驱动**：只有标注 `@DataPermission` 的 Mapper 方法才会被处理，未注解的查询保持原样。
- **依赖隔离**：JSQLParser 4.6 被 shade 到 `com.ty.dp.shaded.jsqlparser`，不会与宿主已有的 JSQLParser 版本冲突。
- **Java 8 字节码**，Spring Boot 2.7.x / MyBatis 3.5.x 验证通过。

## 模块

| 模块 | 作用 |
| --- | --- |
| `data-permission-core` | 规则模型、SQL 改写、Subject 抽象、SPI，不依赖 Spring |
| `data-permission-mybatis` | MyBatis 拦截器、`@DataPermission` / `@DataPermissionIgnore` |
| `data-permission-starter` | Spring Boot 自动装配、JDBC 规则仓库、缓存、管理 API、建表脚本 |
| `data-permission-sample` | H2 示例与 7 个集成测试 |
| `data-permission-ui` | RuoYi-Vue（Vue 2 + Element UI）规则管理页面，可复制进宿主前端 |

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

Spring Security 在 classpath 上时，会自动使用 `SpringSecurityDataPermissionSubjectProvider` 适配当前登录人；否则使用空实现，由调用方通过 `DataPermissionContext.setSubject(...)` 设置。

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

## 文档

- [接入指南（RuoYi / Spring Security / 定时任务 / 灰度上线）](docs/integration.md)
- [规则语义与 SQL 改写规则](docs/rule-semantics.md)
- [管理页面接入（Vue 2 + Element UI）](data-permission-ui/README.md)

## 管理接口

`data-permission.admin.enabled=true` 后提供最小化管理 API（默认前缀 `/data-permission/admin`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/rules?appPoint=&ruleName=` | 规则列表 |
| GET | `/rules/{id}` | 规则详情（含复杂明细） |
| POST | `/rules` | 新增/更新规则，返回 `{id}` |
| DELETE | `/rules/{ids}` | 逻辑删除，多个 id 用逗号分隔 |
| GET | `/roles/{roleId}/rules` | 角色已绑定的规则 id |
| PUT | `/roles/{roleId}/rules` | 覆盖角色规则，body：`{"ruleIds":["r1"]}` |

接口本身不做鉴权，**启用前必须由宿主项目在网关或 Spring Security 中限制访问**，并在规则变更后调用缓存的失效逻辑（管理 API 已自动失效当前实例）。

仓库内的 [`data-permission-ui`](data-permission-ui/README.md) 提供可直接复制进 RuoYi-Vue 的管理页面：规则列表、简单/复杂规则编辑、角色规则分配，并带一个本地 Mock 演示，方便不接后端先看交互。

## 构建与测试

```bash
mvn -B -ntp verify
```

示例模块使用 H2 内存库，覆盖简单规则、复杂规则、超级管理员、无规则拒绝、`@DataPermissionIgnore`、PageHelper 分页计数和 `LOG_ONLY` 模式。

## CI/CD

- `ci.yml`：push / PR 时在 Java 8 和 17 上执行后端构建与测试，同时执行管理页面的 `npm run build`。
- `release.yml`：推送 `v*` 标签时，把版本改为标签版本、构建、发布到 GitHub Packages，并创建 GitHub Release 附带三个模块的 jar。

```bash
git tag v0.1.0
git push origin v0.1.0
```

## 兼容性

- Java 8+（字节码目标 8）
- Spring Boot 2.x（`javax.servlet`）
- MyBatis 3.5.x；PageHelper 已通过集成测试，MyBatis-Plus 同样基于 MyBatis `Interceptor`，设计上可共存（本仓库未覆盖 MP 场景）
- 仅拦截 `SELECT`，且仅处理标注了 `@DataPermission` 的 Mapper 方法
- 管理页面：Vue 2 + Element UI 2.15（RuoYi-Vue 技术栈），不依赖 RuoYi 全局组件；Vue 3（RuoYi-Vue-Plus）需迁移组件语法

## License

[MIT](LICENSE)

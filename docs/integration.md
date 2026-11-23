# 接入指南

本文以一个 RuoYi 风格的项目（`by-admin` / `by-system` / `by-common`）为例，说明从引入依赖到灰度上线的完整步骤。其他 Spring Boot 2.x + MyBatis 项目步骤相同。

## 1. 引入依赖

```xml
<dependency>
    <groupId>com.ty</groupId>
    <artifactId>data-permission-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

依赖关系：

- `data-permission-starter` 传递 `data-permission-core` 和 `data-permission-mybatis`
- MyBatis 是 `provided` 作用域，由宿主项目的 `mybatis-spring-boot-starter` / RuoYi 自带依赖提供
- JSQLParser 已 shade 进 core，宿主无需也无法直接引用 `net.sf.jsqlparser`

## 2. 建表

执行 [`schema-mysql.sql`](../data-permission-starter/src/main/resources/db/schema-mysql.sql)。

> 正式库 MySQL 若开启了 GTID，请避免 `CREATE TABLE ... AS SELECT`，建表与数据迁移分开执行。

## 3. 配置

```yaml
data-permission:
  enabled: true
  mode: LOG_ONLY            # 首次上线建议先用 LOG_ONLY
  default-policy: DENY
  no-subject-policy: ALLOW
  admin-user-ids:
    - "1"                   # RuoYi 默认管理员 userId
  cache-ttl-seconds: 300
  table:
    rule: sys_permission_rule
    rule-item: sys_permission_rule_item
    role-rule: sys_role_permission_rule
  admin:
    enabled: false
```

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 总开关；`false` 时拦截器不注册 |
| `mode` | `ENFORCE` | `OFF` / `LOG_ONLY` / `ENFORCE` |
| `default-policy` | `DENY` | 有登录用户但没有匹配规则时的策略；`DENY` 追加 `1 = 0` |
| `no-subject-policy` | `ALLOW` | 无登录上下文时的策略；定时任务默认放行 |
| `admin-user-ids` | `["1"]` | 超级管理员 id，命中后完全跳过规则 |
| `cache-ttl-seconds` | `300` | 规则缓存 TTL |
| `table.*` | 见上 | 三张表的表名，列名固定 |
| `admin.enabled` | `false` | 是否启用管理接口 |
| `admin.path` | `/data-permission/admin` | 管理接口前缀（`@RequestMapping` 占位符） |

## 4. 给 Mapper 加注解

只有标注 `@DataPermission` 的 Mapper 方法会被改写。建议按「一个业务列表查询一个应用点」的粒度注解，不要给所有查询批量注解。

```java
@Mapper
public interface ProjectMapper {

    /** 列表查询：按 project 应用点匹配规则 */
    @DataPermission(appPoint = "project")
    List<Project> selectProjectList(Project project);

    /** 详情：通常也需要，防止越权查看 */
    @DataPermission(appPoint = "project")
    Project selectProjectById(Long id);

    /** 下拉框、内部校验等明确不需要过滤的查询 */
    @DataPermissionIgnore
    List<Project> selectAllForSelect();
}
```

XML Mapper 不需要改 SQL，注解加在接口方法上即可。

### 表别名

规则里的字段默认使用查询中的实际别名（`from project p` → `p.dept_id`）。如果 SQL 是动态拼接、拦截器拿不到稳定别名，可在注解上显式指定：

```java
@DataPermission(appPoint = "project", tableAlias = {"project:p"})
```

### 多表查询

一条 SQL 中出现的每张受保护表都会各自附加条件，表与表之间是 `AND`。规则只会作用于出现在主查询 `FROM` / `JOIN` 中的物理表；如果受保护表只出现在 `JOIN` 的子查询里，请把该子查询拆成独立的 Mapper 查询，或显式加注解并确认 SQL 形态。

## 5. 当前用户适配

### Spring Security（RuoYi 默认）

starter 检测到 `SecurityContextHolder` 后自动注册反适配器，通过反射读取：

- 用户 id：`getUserId()` → `getId()` → `getUsername()`
- 角色：`principal.getRoles()` → `principal.getUser().getRoles()`（RuoYi `LoginUser` 走这条）
- 字段：principal 及其 `user` 对象上的同名属性；`deptId` 有专门回退

因此 RuoYi 项目通常不需要写任何适配代码。

### 自定义认证体系（Sa-Token 等）

注册一个 `DataPermissionSubjectProvider` Bean 即可覆盖默认实现：

```java
@Bean
public DataPermissionSubjectProvider dataPermissionSubjectProvider() {
    return () -> {
        LoginUser loginUser = StpUtil.getLoginIdAsObj() ...;
        if (loginUser == null) {
            return null;
        }
        return SimpleDataPermissionSubject.builder()
                .subjectId(String.valueOf(loginUser.getUserId()))
                .superAdmin("1".equals(String.valueOf(loginUser.getUserId())))
                .roleIds(loginUser.getRoleIds())
                .field("deptId", loginUser.getDeptId())
                .field("userNo", loginUser.getUserNo())
                .build();
    };
}
```

`SimpleDataPermissionSubject` 的 `field(...)` 会用于 `#{字段名}` 和复杂规则的 `CURRENT_USER_FIELD`。

## 6. 定时任务与异步线程

Web 请求由 `DataPermissionContextFilter` 在请求开始时设置 Subject、结束时清理。以下场景没有 Subject：

- Quartz 定时任务
- `@Async` / 线程池
- 启动时的初始化逻辑

此时按 `no-subject-policy` 处理：默认 `ALLOW`，即定时任务不受数据权限影响。若生产要求严格（`DENY`），需要显式指定身份或绕过：

```java
// 以某个用户身份执行
DataPermissionContext.runAs(subject, () -> mapper.selectProjectList(query));

// 明确绕过（例如内部对账、数据修复）
DataPermissionContext.without(() -> mapper.selectProjectList(query));
```

`@Async` 方法不会自动继承 ThreadLocal，需要在异步方法内部重新 `runAs`，或使用 `DataPermissionContext.without`。

## 7. 灰度上线

推荐步骤：

1. `mode: LOG_ONLY`，`default-policy: ALLOW`，先观察日志：
   ```
   [data-permission] user=1001 mapper=...ProjectMapper.selectProjectList
   original=select ... rewritten=/*data-permission*/ SELECT ... WHERE (p.dept_id = 100)
   ```
2. 核对每条 SQL：应用点是否命中、字段是否正确、值是否为空、count SQL 是否包含条件。
3. 把 `default-policy` 改为 `DENY`，继续观察是否出现 `1 = 0`（说明该用户没有规则）。
4. 切到 `ENFORCE`。
5. 保留 `LOG_ONLY` 作为排障开关。

SQL 解析失败时：

- `LOG_ONLY`：记录 error，执行原 SQL
- `ENFORCE`：抛出 `DataPermissionException`，拒绝查询

因此上线前务必用 `LOG_ONLY` 扫一遍复杂 SQL（Oracle 特殊语法、提示符、非标准函数等）。确实无法解析的查询，用 `@DataPermissionIgnore` 或 `DataPermissionContext.without` 明确豁免，并在代码里注明原因。

## 8. 管理接口与安全

`data-permission.admin.enabled=true` 后会自动注册 `/data-permission/admin/**`。接口不做鉴权，RuoYi 项目建议：

- 在 `SecurityConfig` 中要求 `hasRole('admin')` 或对应权限
- 或仅在内部网关暴露
- 规则变更后，多实例部署依赖 `cache-ttl-seconds` 自动过期；管理 API 只会失效当前实例的缓存。若需要秒级生效，可接入 Redis 发布订阅调用 `CachedDataPermissionRuleResolver.invalidateAll()`

## 9. 缓存与多实例

规则缓存 key = 排序后的角色 id 集合 + 应用点。默认 TTL 5 分钟。多实例部署、且要求规则修改立即生效时，有两种做法：

1. 缩短 `cache-ttl-seconds`（如 30）
2. 用消息/Redis 广播，让每个实例调用 `invalidateAll()`

角色变更（用户换角色）不需要额外处理：key 中包含角色集合，换角色后自然命中不同缓存。

## 10. 与现有 `@DataScope` 的关系

RuoYi 自带的 `@DataScope` 是在 Service 层拼接 `params.dataScope`，依赖 XML 里的 `${params.dataScope}`，属于「改 SQL 片段」方案。本项目的规则在 MyBatis 执行前统一改写，两者可以并存，但不建议对同一个 Mapper 同时使用：条件会重复。迁移时：

- 保留 `@DataScope` 的部门树逻辑（如需递归部门），把它翻译成复杂规则
- 逐步去掉 XML 中的 `${params.dataScope}`，改为 `@DataPermission`

## 11. 排查清单

| 现象 | 排查点 |
| --- | --- |
| SQL 没有被改写 | Mapper 方法是否标注 `@DataPermission`；`enabled` / `mode` 是否为关闭；`appPoint` 是否与规则匹配 |
| 查询返回空 | `default-policy=DENY` 且该用户没有规则；规则表名与 SQL 中的表名不一致；动态值解析为 null |
| 分页总数不对 | 确认 starter 在 PageHelper 之后装配（默认已处理）；不要手动调整拦截器顺序 |
| 规则不生效 | 角色 id 是否为字符串形式；`sys_role_permission_rule.is_del` 是否为 `0`；`enabled` 是否为 `1` |
| 复杂规则结果不符 | `group_no` 组间 OR、`sub_group_no` 子组间 AND；明细的 `logic_operator` 从第二条开始生效 |
| 定时任务被过滤/报错 | 检查 `no-subject-policy`；任务内是否需要 `runAs` / `without` |
| 解析异常 | 用 `LOG_ONLY` 找到具体 SQL；必要时 `@DataPermissionIgnore` 豁免 |


## 0.3.0 配置式接入

多数据源宿主设置 `data-permission.datasource-bean: masterDataSource`，管理鉴权设置 `data-permission.admin.authorization.{list,save,edit,remove}`，无需自定义 Bean。关系型范围通过复杂规则 `SUBQUERY` 表达，详情及完整 RuoYi 配置见 [升级说明](upgrade-0.3.0.md)。

# 升级到 0.3.0

main 分支继续使用 Java 8、原生 MyBatis、Spring Boot 2.x。没有引入 RuoYi 依赖。

## RuoYi 最小接入（不写 Bean）

依赖版本改为 `com.ty:data-permission-starter:0.3.0`。先在规则库执行
[增量迁移](../data-permission-starter/src/main/resources/db/upgrade-0.2.0-to-0.3.0-mysql.sql)，只扩容 `right_expr` 至 4000 字符，保留 NULL 默认值与旧数据；自定义表名时替换脚本表名。全新安装用 `schema-mysql.sql`。

```yaml
data-permission:
  enabled: true
  mode: LOG_ONLY
  datasource-bean: masterDataSource
  admin:
    enabled: true
    authorization:
      list: "@ss.hasPermi('system:dataPermission:list')"
      save: "@ss.hasPermi('system:dataPermission:add') or @ss.hasPermi('system:dataPermission:edit')"
      edit: "@ss.hasPermi('system:dataPermission:edit')"
      remove: "@ss.hasPermi('system:dataPermission:remove')"
```

`masterDataSource` 是宿主已存在的真实 Bean 名，`ss` 是宿主现有权限服务。无需新增配置类。规则库的数据源不改变业务 Mapper 使用的数据源。子查询在业务查询的连接执行，因此关系表须能从业务数据源读取（需要跨 schema 时使用相应限定名）。

默认空 `datasource-bean` 保留唯一/主候选数据源选择行为；显式指定时通过 ApplicationContext/ObjectProvider 延迟解析，首次规则访问时名称不存在或类型错误会报错，不会偷偷回退至其他库。仓库与 AdminService 共用选择逻辑；管理写入使用所选真实 DataSource 的事务，保留单库宿主原有事务参与行为。

管理鉴权优先级：宿主 `DataPermissionAdminAuthorizer` Bean > 表达式默认实现 > 0.2.0 告警并放行。只要配置任一动作，缺少的其他动作、求值 false、异常均拒绝（403）。GET=LIST，POST=SAVE（兼容原先新增/修改复用 POST），PUT=EDIT，DELETE=REMOVE。原路径和响应格式不变。

表达式使用 ApplicationContext BeanResolver，属于可信部署配置，**不要提供普通用户编辑入口**。宿主原有登录认证、CSRF 和 CORS 仍适用。

## 在 Vue2 页面配置 PPM 关系型范围

选择复杂规则，表名与应用点对应已标记的业务 Mapper。下表每行均为一条明细；子组设为 1，同组只有一行，明细组合方式用 AND。组间自动 OR。

任务规则：目标表 `ppm_task`（若真实表名不同请替换），应用点填现有任务 Mapper 的应用点。

| 组 | 左字段 | 操作符 | 右值类型 | 右值 |
| --- | --- | --- | --- | --- |
| 1 | assignee_user_id | = | CURRENT_USER_FIELD | userId |
| 2 | id | IN | SUBQUERY | `SELECT task_id FROM ppm_task_coworker WHERE user_id = #{userId}` |
| 3 | project_id | IN | SUBQUERY | `SELECT id FROM ppm_project WHERE is_del='0' AND (leader_user_id = #{userId} OR created_by_user_id = #{userId})` |

项目规则：目标表 `ppm_project`。

| 组 | 左字段 | 操作符 | 右值类型 | 右值 |
| --- | --- | --- | --- | --- |
| 1 | leader_user_id | = | CURRENT_USER_FIELD | userId |
| 2 | created_by_user_id | = | CURRENT_USER_FIELD | userId |

将规则分配给现有角色。先用 LOG_ONLY 对照日志验证，确认范围后改为 ENFORCE；LOG_ONLY 不过滤实际业务结果。

SUBQUERY 编辑器提供多行 SQL 输入框与用户占位符插入，保存前调用 `POST /rules/validate`，保存接口再次校验。`GET /subquery-fields` 返回实际白名单，两者都使用原有管理鉴权。仓库目前只有 Vue2 页面，Vue3 接入可复用这两个接口与同样的规则 JSON。

## 子查询安全与绑定约定

- 仅允许操作符 IN，模板必须是单条 SELECT（含嵌套 SELECT/UNION），查询结果需为与左列匹配的一列。
- 最大 4000 字符；拒绝注释（包括出现在字符串里的注释标记）、分号、DDL/DML、INTO/OUTFILE、锁定读、会话变量、裸问号和命名参数，以及 `${...}`。错误带 `SUBQUERY:` 前缀；管理 API 校验失败返回 400。
- 固定用户字段白名单：`userId`、`deptId`、`username`、`userName`、`userNo`、`roleIds`、`visibleTaskIds`、`visibleProjectIds`。占位符写作 `#{userId}`，不加引号，不支持路径、任意表达式或 MyBatis 类型参数。不读取请求参数。
- 用户字段缺失或集合为空时拒绝执行；集合在 `IN (#{roleIds})` 中展开成独立绑定参数。`visibleTaskIds`/`visibleProjectIds` 仅为兼容已有 subject 的字段，不会自动计算；PPM 示例通过关系表子查询实现，无需这两个预计算字段。
- 为防止函数副作用，仅允许聚合/标量函数 COUNT、MIN、MAX、SUM、AVG、COALESCE、IFNULL、NULLIF、LOWER、UPPER、TRIM、CAST；拒绝自定义函数、带 schema 的函数和带引号的函数名。
- 子查询中的表别名及列限定保持模板原样；左字段按业务主查询别名限定。模板不能引用不稳定的业务主查询别名，建议写自包含子查询。
- 参数按最终 SQL 的出现顺序绑定；重复占位符每个出现位置都有参数值，同一规则不会被重复注入。foreach 附加参数保留。PageHelper 从改写后的 SQL 派生 count/page SQL。
- 保留替换 MappedStatement 后由 MyBatis 重建 CacheKey 的机制，查询 CacheKey 包含完整 SQL（含子查询内容）和所有绑定值。角色规则缓存先按角色集合/应用点定位内容 key，内容 key 包含完整子查询 SQL，页面保存后两级缓存均失效，外部直接改表需主动清缓存或等待 TTL。
- core 直接调用者使用新增 `SqlConditionBuilder.applyPrepared(..., bindings)` 获取模板和绑定映射；原 `apply` 对旧规则保持兼容，对 SUBQUERY 拒绝无绑定的调用。starter 自动完成参数绑定。

## 可以删除哪些宿主类

**本任务所述的三个 Bean 都可以删除**：

1. 自定义 RuleRepository：改为 `datasource-bean: masterDataSource`。
2. 计算 PPM 可见任务/项目 ID 的 SubjectProvider：改用上面的页面关系型规则，默认 Spring Security provider 读取当前用户。subjectId 现在按 `userId → user.userId → id → username` 解析，两参 LoginUser 的嵌套用户 ID 可正确匹配 `admin-user-ids: ["1"]`。
3. 自定义 AdminAuthorizer：改用上面的配置表达式；保留 Bean 时它仍优先，以兼容 0.2.0。

这里的零配置类建立在宿主已有 Spring Security 登录对象、数据源、权限服务和受保护 Mapper 注解的基础上；不改变已有业务代码。

## 验证与安装

```sh
mvn -B -ntp clean verify
mvn -B -ntp install
cd data-permission-ui
npm ci
npm run build
```

verify 覆盖旧规则回归、子查询实际 JDBC 绑定、注入值隔离、别名、重复参数、UNION、嵌套查询、PageHelper count、foreach、保存后缓存失效、非法模板、数据源选择、SpEL 和 subjectId 回退。

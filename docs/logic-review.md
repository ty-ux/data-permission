# 数据权限引擎逻辑审查说明

审查日期：2026-11-20。本文记录当前工作区版本的逻辑审查结果，供修复和回归验证使用。

> 本文保留审查当时的复现与状态，属于历史记录；“待修复”和测试数量不代表当前分支 HEAD 的状态。后续修复请结合 Git 提交历史与当前回归测试核对。

本次未修改业务源码。共复现 5 个问题，其中自连接和嵌套查询问题可导致权限过滤遗漏。现有 46 个测试全部通过，说明当前测试尚未覆盖这些边界场景，不能据此判定权限逻辑完整。

## 1. 审查范围与问题汇总

本次重点检查 SQL 条件生成、MyBatis 拦截器、规则加载及缓存失效逻辑。没有对前端交互、生产数据库兼容性或所有 SQL 语法做完整验证。

本文中的 P1 表示应优先修复，涉及越权读取、旧权限继续生效或常用查询不可用；P2 表示特定配置下的功能错误。

| 编号 | 优先级 | 问题 | 直接影响 | 状态 |
| --- | --- | --- | --- | --- |
| DP-01 | P1 | 自连接的表别名被覆盖 | 部分表实例未受权限限制，返回其他部门数据 | 已复现，待修复 |
| DP-02 | P1 | 嵌套查询遍历不完整 | 外层表或标量子查询漏过滤 | 已复现，待修复 |
| DP-03 | P1 | 四参数查询路径丢失动态 SQL 附加参数 | `foreach` 查询抛出参数绑定异常 | 已复现，待修复 |
| DP-04 | P1 | 缓存失效后仍可回填旧规则 | 权限收紧后旧权限可能持续到缓存过期 | 已复现，待修复 |
| DP-05 | P2 | 带引号常量未正确规范化 | 按文档配置字符串常量后生成非法 SQL | 已复现，待修复 |

## 2. SQL 复现的公共数据

DP-01、DP-02、DP-05 使用以下 H2 数据：

```sql
CREATE TABLE project (
    id BIGINT,
    dept_id INT,
    status VARCHAR(20)
);

INSERT INTO project VALUES (1, 100, 'A'), (2, 200, 'A');
```

除 DP-05 外，权限规则为：

```text
tableName     = project
fieldName     = dept_id
conditionType = =
conditionValue = #{deptId}
```

当前用户不是超级管理员，用户字段 `deptId = 100`，使用 `denyWhenEmpty = true`。预期用户只能读取部门 100 的数据，即 `id = 1`。

## 3. DP-01：自连接只过滤最后一个别名

### 代码位置与原因

[`SqlConditionBuilder.putAlias`](../data-permission-core/src/main/java/com/ty/dp/core/sql/SqlConditionBuilder.java)（审查时第 248–251 行）用 `Map<String, String>` 保存“表名 → 别名”。同一物理表在一条 SQL 中出现多次时，后面的别名覆盖前面的别名，最终只为一个表实例生成权限条件。

### 复现与实际结果

输入 SQL：

```sql
SELECT a.id
FROM project a CROSS JOIN project b
WHERE b.id = 1;
```

实际改写 SQL（省略多余括号）：

```sql
SELECT a.id
FROM project a CROSS JOIN project b
WHERE b.dept_id = 100 AND b.id = 1;
```

实际返回 `id = 1, 2`。权限条件只限制 `b`，用于返回数据的 `a` 未受限制，导致部门 200 的记录泄露。

### 修复建议与验收

按当前查询作用域保存每个表实例及其别名，分别对 `a`、`b` 应用该表规则。字段限定名也需要按当前表实例解析，不能继续使用覆盖式映射。

回归测试至少覆盖同表两个别名、三个别名及关联条件场景；上述示例只能返回 `id = 1`。涉及外连接时，应单独验证条件放入 `ON` 或 `WHERE` 后的业务语义。

## 4. DP-02：嵌套查询遍历不完整

### 代码位置与原因

[`SqlConditionBuilder.addToBody`](../data-permission-core/src/main/java/com/ty/dp/core/sql/SqlConditionBuilder.java)（审查时第 139–146 行）在发现 `FROM` 是 `SubSelect` 时直接递归并返回，跳过当前查询层级的其他表。当前实现也没有遍历 SELECT 列中的标量子查询。

文档已说明 JOIN 右侧子查询不在自动处理范围内，但下面两种复现并不只是这一已知限制。

### 场景 A：FROM 子查询导致外层 JOIN 表被跳过

输入 SQL：

```sql
SELECT p.id
FROM (SELECT id FROM project WHERE id = 1) x
CROSS JOIN project p;
```

实际改写结果：

```sql
SELECT p.id
FROM (
    SELECT id FROM project
    WHERE project.dept_id = 100 AND id = 1
) x
CROSS JOIN project p;
```

实际返回 `id = 1, 2`。内层查询被过滤，外层 `p` 未被过滤。

### 场景 B：SELECT 列中的子查询泄露全表统计

输入 SQL：

```sql
SELECT p.id, (SELECT COUNT(*) FROM project) AS total
FROM project p;
```

实际只在外层追加 `p.dept_id = 100`，返回 `id = 1, total = 2`。其中 `total` 包含无权读取的部门 200 的数据；若统计子查询也遵守相同规则，预期应为 `total = 1`。

### 修复建议与验收

按查询作用域遍历 AST，在处理内层查询后继续处理外层表，覆盖 FROM、JOIN、SELECT 列和 WHERE 表达式中的子查询。别名解析需要区分作用域，避免同名别名串用。

对暂时无法安全处理的结构，在 `ENFORCE` 模式下明确拒绝执行。回归测试应保证场景 A 只返回 `id = 1`、场景 B 返回 `total = 1`，并补充相关子查询、CTE、UNION 和分页 count 包装的验证。

## 5. DP-03：foreach 附加参数在改写后丢失

### 代码位置与原因

[`DataPermissionInterceptor.rewriteAndProceed`](../data-permission-mybatis/src/main/java/com/ty/dp/mybatis/DataPermissionInterceptor.java)（审查时第 168–181 行）使用 `StaticSqlSource` 构造替代语句，仅传递 SQL 和 `ParameterMapping`。`additionalParameters` 只在六参数查询路径复制，四参数路径没有保留。

MyBatis 的 `foreach` 会生成 `__frch_id_0` 等附加参数。这些参数通常不在原始 Mapper 参数对象中，因此不能仅靠原始参数对象恢复。

### 复现与实际结果

在标注 `@DataPermission` 的 Mapper 上执行：

```xml
<script>
  SELECT id FROM project WHERE id IN
  <foreach collection="ids" item="id" open="(" separator="," close=")">
    #{id}
  </foreach>
</script>
```

方法参数使用 `@Param("ids") List<Long> ids`，传入 `[1, 2]`。SQL 可以成功改写，但执行时抛出：

```text
Parameter '__frch_id_0' not found. Available parameters are [ids, param1]
```

### 修复建议与验收

替代 `SqlSource` 返回的 `BoundSql` 应保留原始参数映射、参数对象和全部附加参数，四参数与六参数查询路径保持一致。

回归测试应覆盖 `foreach`、`bind`、嵌套动态参数以及分页插件组合。上述查询应正常执行并只返回 `id = 1`。

## 6. DP-04：缓存清空后仍可写回旧规则

### 代码位置与原因

[`CachedDataPermissionRuleResolver.resolve`](../data-permission-starter/src/main/java/com/ty/dp/starter/CachedDataPermissionRuleResolver.java)（审查时第 44–47 行）先读取仓库，再写入缓存。该过程与 `invalidateAll()` 没有协调机制；`ConcurrentHashMap` 只能保证单次操作的线程安全，不能保证整个加载和失效流程的顺序。

### 复现与实际结果

使用两个线程及 `CountDownLatch` 固定执行顺序：

1. 请求 A 缓存未命中，从仓库取得旧规则 `dept_id = 100`，在返回前暂停。
2. 将仓库规则更新为 `dept_id = 200`。
3. 调用 `invalidateAll()` 清空缓存。
4. 恢复请求 A，A 将旧规则写入缓存。
5. 再次解析相同角色和应用点的规则。

实际结果仍为 `dept_id = 100`，复现输出为 `CACHE_AFTER_INVALIDATION=100`。如果规则变更用于收回权限，旧权限可能一直保留到这次回填的缓存项过期。

### 修复建议与验收

为缓存加载与失效增加版本校验或同步机制，确保失效前开始的加载不能在失效后发布旧结果。版本检查与缓存发布本身也需要协调，避免检查后再次发生失效。

另外，管理服务目前在事务方法内调用失效。修复时应将失效安排在事务成功提交后，避免其他请求在提交前重新加载旧数据库数据。这是代码检查发现的相关风险，本次并发复现未使用真实数据库事务验证该时序。

回归测试应使用同步信号控制线程顺序，验证上述过程完成后解析到新规则，并补充事务提交与回滚场景。单纯增加 TTL 或在提交后清缓存，不能单独解决所有旧加载回填问题。

## 7. DP-05：带引号的字符串常量生成非法 SQL

### 代码位置与原因

[`SqlConditionBuilder.resolveValue`](../data-permission-core/src/main/java/com/ty/dp/core/sql/SqlConditionBuilder.java)（审查时第 508–526 行）没有去掉普通常量外层引号，随后 `createValueExpression` 又进行转义和字符串字面量构造。

[`rule-semantics.md`](rule-semantics.md) 推荐使用 `'PENDING'` 强制指定字符串常量，并说明引号会被去掉；当前实现与该说明不一致。

### 复现与实际结果

规则字段改为 `status`、操作符为 `=`，右值配置为 `'A'`。实际生成：

```sql
SELECT id FROM project WHERE project.status = ''A'';
```

H2 执行时报语法错误。对照配置为不带引号的 `A` 时，会生成合法的 `project.status = 'A'` 并返回两条记录。

### 修复建议与验收

在规则解析阶段明确区分带引号的字符串常量、动态字段引用和裸标识符，统一去除外层定界符并处理内部转义。带引号的常量应直接作为常量，避免再次尝试按用户字段解析。

回归测试应覆盖 `'A'`、`'PENDING'`、含单引号的字符串，以及与用户字段同名的强制字符串常量。上述例子应生成 `status = 'A'`，且执行成功。

## 8. 验证记录与限制

本次执行了 `mvn -B -ntp verify`：

- 现有测试共 46 个，失败 0、错误 0、跳过 0。
- core、mybatis、starter 模块构建成功。
- sample 在 Spring Boot `repackage` 阶段失败，错误为无法将 sample jar 重命名为 `.jar.original`。
- 因此测试阶段通过，但完整构建未通过；该重命名失败的根因未进一步确认，不能直接归因于业务代码。

补充最小复现使用项目测试依赖中的 H2 和 MyBatis。临时文件位于 `data-permission-sample/target/ReviewProbe.java`，编译结果位于该模块的 `target/review-classes`，均属于构建目录，执行 `clean` 后可能被删除。本文保留了关键输入、执行顺序及结果，后续应将这些场景转为正式回归测试。

本次未验证这些问题在所有数据库和分页插件版本上的表现，也未证明其他未覆盖路径不存在问题。

## 9. 建议修复顺序

1. 先修复 DP-01、DP-02，封闭已复现的权限遗漏路径。
2. 修复 DP-04，保证权限收回后缓存不会重新发布旧规则。
3. 修复 DP-03，恢复动态 SQL 查询的正常参数绑定。
4. 修复 DP-05，并使常量解析行为与文档一致。
5. 将上述复现场景纳入回归测试，重新执行完整构建并验证分页列表与 count 的权限一致性。

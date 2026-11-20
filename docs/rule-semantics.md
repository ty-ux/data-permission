# 规则语义

本文说明规则如何变成 SQL 条件，以及组合、取值、失败时的行为。理解这几条规则后，排查问题基本只需要看日志里的 `original` 和 `rewritten`。

## 1. 规则模型

一条规则（`sys_permission_rule`）绑定一张物理表，通过 `sys_role_permission_rule` 绑定角色。

| 字段 | 说明 |
| --- | --- |
| `app_point` | 应用点。Mapper 注解上的 `appPoint` 与它相等才命中；为空/NULL 的规则对所有应用点生效 |
| `table_name` | 目标表名。与 SQL 中的表名比较时忽略库名、引号和大小写 |
| `field_name` | 简单规则的左值字段 |
| `condition_type` | 简单规则操作符：`=` `!=` `>` `>=` `<` `<=` `LIKE` `IN` `IS_NULL` `IS_NOT_NULL` |
| `condition_value` | 简单规则右值，支持常量、`#{字段}`、裸字段名 |
| `rule_mode` | `SIMPLE` / `COMPLEX` |
| `logic_operator` | 与同表上一条规则组合时使用；第一条忽略 |
| `priority` | 越大越先 |
| `sort_order` | 同优先级时越小越先 |
| `enabled` | `1` 启用 |

规则加载条件：`is_del = '0'` 且 `enabled = '1'`，并且角色命中。

## 2. 简单规则

### 常量

```
field_name = dept_id
condition_type = =
condition_value = 100
```

生成 `p.dept_id = 100`。

### 动态值 `#{字段}`

```
condition_value = #{deptId}
```

取值顺序：

1. 当前 `DataPermissionSubject.getField("deptId")`
2. Mapper 方法的参数对象上的同名属性

两者都取不到 → 该规则生成 `1 = 0`（不会退化成常量字符串）。

### 裸标识符

```
condition_value = PENDING
```

先尝试按用户字段/参数属性解析；解析不到时作为字符串常量 `'PENDING'`。想强制使用字符串常量时，建议加上引号：`'PENDING'`（引号会被去掉，值仍是 `PENDING`）。

### 操作符

| 操作符 | 生成 | 说明 |
| --- | --- | --- |
| `=` / `!=` / `>` / `>=` / `<` / `<=` | 对应比较 | 值按类型渲染：数字、布尔、日期、字符串 |
| `LIKE` | `column LIKE '%value%'` | 自动加首尾 `%` |
| `IN` | `column IN (...)` | 值可以是集合（如用户字段是 `List`），或逗号分隔字符串 |
| `IS_NULL` | `column IS NULL` | 忽略右值 |
| `IS_NOT_NULL` | `column IS NOT NULL` | 忽略右值 |

日期值渲染为 `'yyyy-MM-dd HH:mm:ss'` 字符串字面量。

## 3. 复杂规则

复杂规则的明细存 `sys_permission_rule_item`：

```
(group_no=1, sub_group_no=1): dept_id = #{deptId}
(group_no=1, sub_group_no=2): owner_no = #{userNo}
(group_no=2): create_by = #{userNo}
```

组合方式：

- **组间**（`group_no` 不同）：`OR`
- **子组间**（同组内 `sub_group_no` 不同）：`AND`
- **子组内**（同 `sub_group_no`）：按 `sort_order` 依次用明细自身的 `logic_operator` 组合；第一条的 `logic_operator` 不参与

上面的例子生成：

```sql
((p.dept_id = 100 AND p.owner_no = 'u1') OR p.create_by = 'u1')
```

### 明细右值类型

| `right_type` | 右值来源 |
| --- | --- |
| `CONST` | 常量或 `#{字段}`（同简单规则） |
| `CURRENT_USER_FIELD` | 只从当前用户取值；取不到 → `1 = 0` |
| `FIELD` | 另一个字段，例如 `a.create_by` 或 `other_table.owner_no` |
| `NONE` | 不使用右值（`IS_NULL` / `IS_NOT_NULL`） |

左值 `left_expr` 支持 `dept_id` 或 `project.dept_id`；后者在表名与当前受保护表一致时会替换为实际别名。

## 4. 多条规则如何组合

同一张表上命中多条规则时：

1. 按 `priority DESC`、`sort_order ASC`、`id ASC` 排序
2. 从第二条开始，用该规则自身的 `logic_operator` 与前面的结果组合

```
((r1 op2 r2) op3 r3) ...
```

- 想让多角色规则取并集：把后续规则都设为 `OR`
- 想取交集：保持 `AND`（默认）

不同受保护表之间的条件是 `AND`。

## 5. 失败与边界

| 场景 | 行为 |
| --- | --- |
| 用户没有任何命中规则，`default-policy=DENY` | 整条 SQL 追加 `1 = 0` |
| 用户没有任何命中规则，`default-policy=ALLOW` | 不改写，放行 |
| 规则字段名/操作符非法 | 该规则生成 `1 = 0` |
| 动态值解析不到 | 该规则生成 `1 = 0` |
| `IN` 列表为空 | 该规则生成 `1 = 0` |
| SQL 解析失败，`ENFORCE` | 抛出 `DataPermissionException`，查询被拒绝 |
| SQL 解析失败，`LOG_ONLY` | 记录 error，执行原 SQL |
| 没有登录用户，`no-subject-policy=ALLOW` | 不改写，放行（定时任务默认行为） |
| 没有登录用户，`no-subject-policy=DENY` | 追加 `1 = 0` |
| 超级管理员 | 完全跳过 |
| `@DataPermissionIgnore` / `DataPermissionContext.without` | 完全跳过 |

**为什么解析失败不"丢掉条件"？** 丢掉一个 `AND` 条件会扩大数据范围，这正是数据权限最危险的失败方向。本项目所有失败路径都朝"少给数据"的方向收敛。

## 6. 生成的 SQL 形态

改写后的 SQL 带标记前缀，便于日志检索和防止重复改写：

```sql
/*data-permission*/ SELECT id, name FROM project p WHERE (p.dept_id = 100) AND (status = 'A')
```

权限条件作为整体加在原有 `WHERE` 前面：

```sql
WHERE (权限条件) AND (原条件)
```

这样即使用户的 SQL 里有 `OR`，也不会因为运算符优先级被绕过。

### 分页 count

PageHelper 生成 `select count(0) from (原 SQL) table_count` 时，拦截器识别这种外层包装并进入子查询改写，因此 count 与列表使用同一套权限条件。

### UNION

`UNION` / `UNION ALL` 的每个分支分别改写。

### 子查询

主查询 `FROM` 是一个子查询时（典型是 count 包装），递归进入子查询改写。`JOIN` 右侧的子查询不在自动处理范围内；其中的受保护表需要拆分为独立查询或显式豁免。

## 7. 注解与规则匹配范围

- 规则只对标注了 `@DataPermission` 的 Mapper 方法或接口生效
- 注解的 `appPoint` 为空时，只按表名匹配
- 规则表名不在当前 SQL 的表集合中时，该规则被忽略；若最终没有任何条件且 `default-policy=DENY`，整条查询仍会被 `1 = 0` 拒绝
- `@DataPermission` 可以放在接口上作为默认值，方法上的注解覆盖接口注解

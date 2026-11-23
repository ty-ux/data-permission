-- Incremental migration. Existing values and NULL default are preserved.
-- If data-permission.table.rule-item is customized, replace this table name.
ALTER TABLE sys_permission_rule_item
  MODIFY COLUMN right_expr VARCHAR(4000) NULL DEFAULT NULL COMMENT '右值表达式（含 SUBQUERY SQL）';
-- right_type remains VARCHAR with its existing CONST default; no data rewrite.

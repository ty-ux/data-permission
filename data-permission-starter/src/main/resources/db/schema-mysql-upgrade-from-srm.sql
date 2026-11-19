-- Upgrade an existing SRM-style rule schema (sys_permission_rule already has
-- rule_mode / logic_operator / sort_order / is_del and role_id is BIGINT).
-- Run one statement at a time and skip columns that already exist.

ALTER TABLE `sys_permission_rule`
    ADD COLUMN `app_point` VARCHAR(64) NULL COMMENT '应用点，空表示按表名匹配' AFTER `rule_name`;

ALTER TABLE `sys_permission_rule`
    ADD COLUMN `priority` INT NULL DEFAULT 0 COMMENT '优先级，越大越先' AFTER `sort_order`;

ALTER TABLE `sys_permission_rule`
    ADD COLUMN `enabled` CHAR(1) NULL DEFAULT '1' COMMENT '1启用 0停用' AFTER `priority`;

ALTER TABLE `sys_role_permission_rule`
    MODIFY COLUMN `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID';

-- Existing rules keep working: app_point NULL matches every application point,
-- enabled defaults to 1 and priority defaults to 0.

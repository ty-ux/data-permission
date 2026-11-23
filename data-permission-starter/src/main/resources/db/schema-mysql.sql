-- data-permission 0.1.0 schema (MySQL 5.7+/8.0, utf8mb4)
-- Table names are defaults; override them with data-permission.table.* if needed.

CREATE TABLE IF NOT EXISTS `sys_permission_rule`
(
    `id`              VARCHAR(64)  NOT NULL COMMENT '主键',
    `rule_name`       VARCHAR(128) NULL COMMENT '规则名称',
    `app_point`       VARCHAR(64)  NULL COMMENT '应用点，空表示按表名匹配',
    `table_name`      VARCHAR(128) NOT NULL COMMENT '目标表',
    `field_name`      VARCHAR(128) NULL COMMENT '简单规则过滤字段',
    `condition_type`  VARCHAR(20)  NULL COMMENT '简单规则操作符',
    `condition_value` VARCHAR(500) NULL COMMENT '简单规则值',
    `rule_mode`       VARCHAR(20)  NULL DEFAULT 'SIMPLE' COMMENT 'SIMPLE/COMPLEX',
    `logic_operator`  VARCHAR(10)  NULL DEFAULT 'AND' COMMENT '与上一条规则的关系',
    `sort_order`      INT          NULL DEFAULT 0 COMMENT '排序，越小越先',
    `priority`        INT          NULL DEFAULT 0 COMMENT '优先级，越大越先',
    `enabled`         CHAR(1)      NULL DEFAULT '1' COMMENT '1启用 0停用',
    `description`     VARCHAR(500) NULL COMMENT '描述',
    `create_by`       VARCHAR(64)  NULL COMMENT '创建者',
    `create_time`     DATETIME     NULL COMMENT '创建时间',
    `update_by`       VARCHAR(64)  NULL COMMENT '更新者',
    `update_time`     DATETIME     NULL COMMENT '更新时间',
    `is_del`          CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    PRIMARY KEY (`id`),
    KEY `idx_dp_rule_table` (`table_name`, `is_del`),
    KEY `idx_dp_rule_app` (`app_point`, `is_del`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='数据权限规则';

CREATE TABLE IF NOT EXISTS `sys_permission_rule_item`
(
    `id`             VARCHAR(64)  NOT NULL COMMENT '主键',
    `rule_id`        VARCHAR(64)  NOT NULL COMMENT '规则ID',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `group_no`       INT          NOT NULL DEFAULT 1 COMMENT '外层分组，组间OR',
    `sub_group_no`   INT          NOT NULL DEFAULT 1 COMMENT '子分组，子组间AND',
    `logic_operator` VARCHAR(10)  NOT NULL DEFAULT 'AND' COMMENT '与上一条明细的关系',
    `left_type`      VARCHAR(20)  NOT NULL DEFAULT 'FIELD' COMMENT '左值类型',
    `left_expr`      VARCHAR(255) NOT NULL COMMENT '左值表达式',
    `operator`       VARCHAR(20)  NOT NULL COMMENT '操作符',
    `right_type`     VARCHAR(30)  NULL DEFAULT 'CONST' COMMENT 'CONST/CURRENT_USER_FIELD/FIELD/SUBQUERY/NONE',
    `right_expr`     VARCHAR(4000) NULL DEFAULT NULL COMMENT '右值表达式（含 SUBQUERY SQL）',
    `create_by`      VARCHAR(64)  NULL COMMENT '创建者',
    `create_time`    DATETIME     NULL COMMENT '创建时间',
    `update_by`      VARCHAR(64)  NULL COMMENT '更新者',
    `update_time`    DATETIME     NULL COMMENT '更新时间',
    `is_del`         CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    PRIMARY KEY (`id`),
    KEY `idx_dp_rule_item_rule` (`rule_id`, `is_del`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='数据权限规则明细';

CREATE TABLE IF NOT EXISTS `sys_role_permission_rule`
(
    `id`          VARCHAR(64) NOT NULL COMMENT '主键',
    `role_id`     VARCHAR(64) NOT NULL COMMENT '角色ID',
    `rule_id`     VARCHAR(64) NOT NULL COMMENT '规则ID',
    `create_by`   VARCHAR(64) NULL COMMENT '创建者',
    `create_time` DATETIME    NULL COMMENT '创建时间',
    `is_del`      CHAR(1)     NOT NULL DEFAULT '0' COMMENT '0正常 1删除',
    PRIMARY KEY (`id`),
    KEY `idx_dp_role_rule_role` (`role_id`, `is_del`),
    KEY `idx_dp_role_rule_rule` (`rule_id`, `is_del`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色与数据权限规则关系';

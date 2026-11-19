drop table if exists demo_project;
drop table if exists sys_permission_rule;
drop table if exists sys_permission_rule_item;
drop table if exists sys_role_permission_rule;

create table demo_project
(
    id       bigint primary key,
    name     varchar(100),
    dept_id  bigint,
    owner_no varchar(50),
    amount   decimal(12, 2)
);

create table sys_permission_rule
(
    id              varchar(64) not null primary key,
    rule_name       varchar(128),
    app_point       varchar(64),
    table_name      varchar(128) not null,
    field_name      varchar(128),
    condition_type  varchar(20),
    condition_value varchar(500),
    rule_mode       varchar(20) default 'SIMPLE',
    logic_operator  varchar(10) default 'AND',
    sort_order      int         default 0,
    priority        int         default 0,
    enabled         char(1)     default '1',
    description     varchar(500),
    create_by       varchar(64),
    create_time     timestamp,
    update_by       varchar(64),
    update_time     timestamp,
    is_del          char(1)     default '0' not null
);

create table sys_permission_rule_item
(
    id             varchar(64)  not null primary key,
    rule_id        varchar(64)  not null,
    sort_order     int          default 0 not null,
    group_no       int          default 1 not null,
    sub_group_no   int          default 1 not null,
    logic_operator varchar(10)  default 'AND' not null,
    left_type      varchar(20)  default 'FIELD' not null,
    left_expr      varchar(255) not null,
    operator       varchar(20)  not null,
    right_type     varchar(30)  default 'CONST',
    right_expr     varchar(500),
    create_by      varchar(64),
    create_time    timestamp,
    update_by      varchar(64),
    update_time    timestamp,
    is_del         char(1)      default '0' not null
);

create table sys_role_permission_rule
(
    id          varchar(64) not null primary key,
    role_id     varchar(64) not null,
    rule_id     varchar(64) not null,
    create_by   varchar(64),
    create_time timestamp,
    is_del      char(1)     default '0' not null
);

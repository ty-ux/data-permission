insert into demo_project (id, name, dept_id, owner_no, amount)
values (1, 'Alpha', 100, 'u1', 1000.00),
       (2, 'Beta', 100, 'u2', 2000.00),
       (3, 'Gamma', 200, 'u3', 3000.00);

insert into sim_dept (id, name, manager_no)
values (100, '研发', 'u1'),
       (200, '销售', 'u3'),
       (300, '财务', 'u9');

insert into sim_project (id, name, dept_id, owner_no, create_by, status, amount)
values (101, 'P101', 100, 'u1', 'u9', 'A', 100.00),
       (102, 'P102', 100, 'u1', 'u9', 'B', 200.00),
       (103, 'P103', 100, 'u2', 'u9', 'A', 300.00),
       (104, 'P104', 200, 'u3', 'u1', 'B', 400.00),
       (105, 'P105', 200, 'u3', 'u8', 'A', 500.00),
       (106, 'P106', 300, 'u4', 'u9', 'A', 600.00);

insert into sys_permission_rule (id, rule_name, app_point, table_name, field_name,
                                 condition_type, condition_value, rule_mode, logic_operator,
                                 sort_order, priority, enabled, description, is_del)
values ('r1', '本部门', 'demo_project', 'demo_project', 'dept_id',
        '=', '#{deptId}', 'SIMPLE', 'AND', 0, 0, '1', 'dept_id = current user deptId', '0'),
       ('r2', '本部门且本人', 'demo_project', 'demo_project', null,
        null, null, 'COMPLEX', 'AND', 0, 10, '1', 'dept_id = deptId and owner_no = userNo', '0');

insert into sys_permission_rule_item (id, rule_id, sort_order, group_no, sub_group_no,
                                      logic_operator, left_type, left_expr, operator,
                                      right_type, right_expr, is_del)
values ('i1', 'r2', 0, 1, 1, 'AND', 'FIELD', 'demo_project.dept_id', '=',
        'CURRENT_USER_FIELD', 'deptId', '0'),
       ('i2', 'r2', 1, 1, 1, 'AND', 'FIELD', 'demo_project.owner_no', '=',
        'CURRENT_USER_FIELD', 'userNo', '0');

insert into sys_role_permission_rule (id, role_id, rule_id, is_del)
values ('rr1', '10', 'r1', '0'),
       ('rr2', '20', 'r2', '0');

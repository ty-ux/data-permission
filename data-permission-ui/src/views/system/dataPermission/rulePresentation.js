// Display aliases only. Unknown fields remain visible and all stored identifiers are preserved.
export const fieldLabels = {
  assignee_user_id: '执行人', dept_id: '所属部门', owner_no: '负责人编号',
  owner_id: '负责人', create_by: '创建人', project_id: '所属项目', id: '记录编号',
  user_id: '用户', status: '状态', leader_user_id: '项目负责人', created_by_user_id: '项目创建人'
}
export const taskScopes = [
  { label: '我执行的工单', leftExpr: 'assignee_user_id', operator: '=', rightType: 'CURRENT_USER_FIELD', rightExpr: 'userId' },
  { label: '我配合的工单', leftExpr: 'id', operator: 'IN', rightType: 'SUBQUERY', rightExpr: 'SELECT task_id FROM ppm_task_coworker WHERE user_id = #{userId}' },
  { label: '我负责或创建的项目下的工单', leftExpr: 'project_id', operator: 'IN', rightType: 'SUBQUERY', rightExpr: "SELECT id FROM ppm_project WHERE is_del='0' AND (leader_user_id = #{userId} OR created_by_user_id = #{userId})" }
]
export function taskScopeLabel(item, tableName) {
  if (tableName !== 'ppm_task') return ''
  // Only recognize exact, documented templates. Arbitrary SQL is never translated by guessing.
  const scope = taskScopes.find(scope => ['leftExpr', 'operator', 'rightType', 'rightExpr'].every(key => scope[key] === item[key]))
  return scope ? scope.label : ''
}
export const userLabels = {
  userId: '当前用户 ID', deptId: '当前用户所属部门', userNo: '当前用户编号',
  username: '当前用户账号', userName: '当前用户姓名', roleIds: '当前用户角色',
  visibleTaskIds: '当前用户可见的工单', visibleProjectIds: '当前用户可见的项目'
}
export const operatorLabels = {
  '=': '等于', '!=': '不等于', '>': '大于', '>=': '大于或等于', '<': '小于',
  '<=': '小于或等于', LIKE: '包含文字', IN: '属于以下范围', IS_NULL: '为空', IS_NOT_NULL: '不为空'
}
export function fieldLabel(field) {
  const value = String(field || '')
  return fieldLabels[value.split('.').pop()] || value || '待选择字段'
}
export function valueLabel(value) {
  return String(value == null ? '' : value).replace(/#\{(\w+)\}/g, (_, key) => userLabels[key] || '当前用户或参数的「' + key + '」') || '待填写值'
}
export function describeItem(item, tableName) {
  const scopeLabel = taskScopeLabel(item, tableName)
  if (scopeLabel) return scopeLabel
  const left = fieldLabel(item.leftExpr)
  const op = operatorLabels[item.operator] || item.operator || '待选择关系'
  if (item.operator === 'IS_NULL' || item.operator === 'IS_NOT_NULL') return left + ' ' + op
  const right = item.rightType === 'SUBQUERY' ? '关联查询的结果（展开条件查看配置）'
    : item.rightType === 'CURRENT_USER_FIELD' ? (userLabels[item.rightExpr] || '当前用户的「' + (item.rightExpr || '待选择属性') + '」')
      : item.rightType === 'FIELD' ? fieldLabel(item.rightExpr) : valueLabel(item.rightExpr)
  return left + ' ' + op + ' ' + right
}
export function groupItems(items) {
  const groups = []
  items.forEach(item => {
    let group = groups.find(g => g.no === item.groupNo)
    if (!group) { group = { no: item.groupNo, subgroups: [] }; groups.push(group) }
    let subgroup = group.subgroups.find(s => s.no === item.subGroupNo)
    if (!subgroup) { subgroup = { no: item.subGroupNo, items: [] }; group.subgroups.push(subgroup) }
    subgroup.items.push(item)
  })
  groups.sort((a, b) => a.no - b.no).forEach(group => {
    group.subgroups.sort((a, b) => a.no - b.no).forEach(subgroup => {
      // Java's stable sort keeps original order when sortOrder ties.
      subgroup.items.sort((a, b) => a.sortOrder - b.sortOrder)
    })
  })
  return groups
}
export function describeRule(rule) {
  if (rule.ruleMode !== 'COMPLEX') return describeItem({ leftExpr: rule.fieldName, operator: rule.conditionType, rightType: 'CONST', rightExpr: rule.conditionValue })
  if (!(rule.items || []).length) return '尚未添加可见范围'
  return groupItems(rule.items).map(group => group.subgroups.map(subgroup => {
    // The engine folds from left to right; explicit brackets prevent ambiguous mixed AND/OR previews.
    return subgroup.items.reduce((text, item, i) => i ? '（' + text + (item.logicOperator === 'OR' ? ' 或 ' : ' 且 ') + describeItem(item, rule.tableName) + '）' : describeItem(item, rule.tableName), '')
  }).join(' 且 ')).map(text => '【' + text + '】').join(' 或 ')
}

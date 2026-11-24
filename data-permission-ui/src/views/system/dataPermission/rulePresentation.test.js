import test from 'node:test'
import assert from 'node:assert/strict'
import { describeRule, groupItems, taskScopes, taskScopeLabel } from './rulePresentation.js'

const item = (overrides = {}) => ({ groupNo: 1, subGroupNo: 1, sortOrder: 0, leftExpr: 'dept_id', operator: '=', rightType: 'CURRENT_USER_FIELD', rightExpr: 'deptId', ...overrides })

test('preview preserves nested AND/OR and the engine stable ordering without mutating input', () => {
  const items = [item({ id: 'z', leftExpr: 'a' }), item({ id: 'a', leftExpr: 'b', logicOperator: 'OR' }), item({ leftExpr: 'c', sortOrder: 1, logicOperator: 'AND' }), item({ subGroupNo: 2, leftExpr: 'd' }), item({ groupNo: 2, leftExpr: 'e' })]
  const original = JSON.stringify(items)
  assert.deepEqual(groupItems(items)[0].subgroups[0].items.map(i => i.leftExpr), ['a', 'b', 'c'])
  assert.equal(describeRule({ ruleMode: 'COMPLEX', items }), '【（（a 等于 当前用户所属部门 或 b 等于 当前用户所属部门） 且 c 等于 当前用户所属部门） 且 d 等于 当前用户所属部门】 或 【e 等于 当前用户所属部门】')
  assert.equal(JSON.stringify(items), original)
})

test('known PPM scopes are readable while modified queries or other tables stay generic', () => {
  const rule = { tableName: 'ppm_task', ruleMode: 'COMPLEX', items: taskScopes.map((scope, index) => item({ ...scope, groupNo: index + 1 })) }
  assert.equal(describeRule(rule), '【我执行的工单】 或 【我配合的工单】 或 【我负责或创建的项目下的工单】')
  const altered = { ...taskScopes[1], rightExpr: taskScopes[1].rightExpr + ' AND active = 1' }
  assert.equal(taskScopeLabel(altered, 'ppm_task'), '')
  assert.equal(taskScopeLabel(taskScopes[1], 'other_table'), '')
  const preview = describeRule({ ...rule, items: [item(altered)] })
  assert.match(preview, /关联查询的结果/)
  assert.doesNotMatch(preview, /SELECT|WHERE|active/)
})

test('unknown identifiers stay visible and null checks omit the unused value', () => {
  assert.equal(describeRule({ fieldName: 'custom_column', conditionType: 'IS_NULL', conditionValue: 'unused' }), 'custom_column 为空')
  assert.equal(describeRule({ fieldName: 'dept_id', conditionType: '=', conditionValue: '#{deptId}' }), '所属部门 等于 当前用户所属部门')
})

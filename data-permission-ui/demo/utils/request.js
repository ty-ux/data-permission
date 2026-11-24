import { taskScopes } from '../../src/views/system/dataPermission/rulePresentation'

const clone = value => JSON.parse(JSON.stringify(value))
const delay = value => new Promise(resolve => setTimeout(() => resolve(clone(value)), 120))

let rules = [
  {
    id: 'r1',
    ruleName: '本部门项目',
    appPoint: 'project',
    tableName: 'project',
    fieldName: 'dept_id',
    conditionType: '=',
    conditionValue: '#{deptId}',
    ruleMode: 'SIMPLE',
    logicOperator: 'AND',
    sortOrder: 0,
    priority: 10,
    enabled: true,
    description: '只能查看当前用户所属部门的项目',
    items: []
  },
  {
    id: 'r2',
    ruleName: '本部门或本人创建',
    appPoint: 'project',
    tableName: 'project',
    fieldName: null,
    conditionType: null,
    conditionValue: null,
    ruleMode: 'COMPLEX',
    logicOperator: 'AND',
    sortOrder: 0,
    priority: 20,
    enabled: true,
    description: '组 1：本部门且本人负责；组 2：本人创建',
    items: [
      {
        id: 'i1',
        ruleId: 'r2',
        sortOrder: 0,
        groupNo: 1,
        subGroupNo: 1,
        logicOperator: 'AND',
        leftType: 'FIELD',
        leftExpr: 'project.dept_id',
        operator: '=',
        rightType: 'CURRENT_USER_FIELD',
        rightExpr: 'deptId'
      },
      {
        id: 'i2',
        ruleId: 'r2',
        sortOrder: 1,
        groupNo: 1,
        subGroupNo: 2,
        logicOperator: 'AND',
        leftType: 'FIELD',
        leftExpr: 'project.owner_no',
        operator: '=',
        rightType: 'CURRENT_USER_FIELD',
        rightExpr: 'userNo'
      },
      {
        id: 'i3',
        ruleId: 'r2',
        sortOrder: 2,
        groupNo: 2,
        subGroupNo: 1,
        logicOperator: 'OR',
        leftType: 'FIELD',
        leftExpr: 'project.create_by',
        operator: '=',
        rightType: 'CURRENT_USER_FIELD',
        rightExpr: 'userNo'
      }
    ]
  },
  {
    id: 'r3',
    ruleName: '合同创建人',
    appPoint: 'contract',
    tableName: 'contract',
    fieldName: 'create_by',
    conditionType: '=',
    conditionValue: '#{userNo}',
    ruleMode: 'SIMPLE',
    logicOperator: 'AND',
    sortOrder: 0,
    priority: 0,
    enabled: true,
    description: '',
    items: []
  },
  {
    id: 'r4',
    ruleName: '停用示例',
    appPoint: '',
    tableName: 'customer',
    fieldName: 'status',
    conditionType: '=',
    conditionValue: 'A',
    ruleMode: 'SIMPLE',
    logicOperator: 'AND',
    sortOrder: 0,
    priority: 0,
    enabled: false,
    description: '演示停用状态',
    items: []
  }
]

// Reproduce the documented PPM scenario without connecting to production data.
rules.push({
  id: 'r5', ruleName: '项目进度-我的工单', appPoint: 'ppm_task', tableName: 'ppm_task',
  ruleMode: 'COMPLEX', logicOperator: 'OR', sortOrder: 10, priority: 100, enabled: true,
  description: '执行人 / 配合人 / 负责或创建项目',
  items: taskScopes.map(({ label, ...scope }, index) => ({
    ...scope, groupNo: index + 1, subGroupNo: 1, sortOrder: index, logicOperator: 'AND', leftType: 'FIELD'
  }))
})

let roleRules = {
  2: ['r1'],
  3: ['r2']
}

let sequence = 10

function matchRule(rule, params) {
  const appPoint = String(params.appPoint || '').trim().toLowerCase()
  if (appPoint && (rule.appPoint || '').trim().toLowerCase() !== appPoint) {
    return false
  }
  const ruleName = String(params.ruleName || '').trim().toLowerCase()
  if (ruleName && (rule.ruleName || '').toLowerCase().indexOf(ruleName) < 0) {
    return false
  }
  return true
}

export default function request(config) {
  const method = (config.method || 'get').toLowerCase()
  const url = config.url || ''
  const path = url.split('?')[0]
  const params = config.params || {}
  const data = config.data || {}

  // Demo-only fixtures. Production validation always runs in the starter's SQL parser.
  if (method === 'get' && path === '/data-permission/admin/subquery-fields') {
    return delay(['userId', 'deptId', 'username', 'userName', 'userNo', 'roleIds', 'visibleTaskIds', 'visibleProjectIds'])
  }
  if (method === 'post' && path === '/data-permission/admin/rules/validate') {
    return delay({ status: 'ok' })
  }
  if (method === 'get' && path === '/data-permission/admin/rules') {
    const list = rules
      .filter(rule => matchRule(rule, params))
      .sort((left, right) => {
        const priority = (right.priority || 0) - (left.priority || 0)
        if (priority !== 0) {
          return priority
        }
        const sort = (left.sortOrder || 0) - (right.sortOrder || 0)
        return sort !== 0 ? sort : String(left.id).localeCompare(String(right.id))
      })
    return delay(list)
  }

  const rulePath = path.match(/^\/data-permission\/admin\/rules\/([^/]+)$/)
  if (rulePath && method === 'get') {
    const rule = rules.find(item => item.id === decodeURIComponent(rulePath[1]))
    return rule ? delay(rule) : Promise.reject(new Error('规则不存在'))
  }

  if (method === 'post' && path === '/data-permission/admin/rules') {
    const rule = clone(data)
    if (!rule.id) {
      rule.id = 'r' + (++sequence)
    }
    const index = rules.findIndex(item => item.id === rule.id)
    if (index >= 0) {
      rules.splice(index, 1, rule)
    } else {
      rules.push(rule)
    }
    return delay({ id: rule.id })
  }

  if (rulePath && method === 'delete') {
    const ids = decodeURIComponent(rulePath[1]).split(',')
    rules = rules.filter(rule => ids.indexOf(rule.id) < 0)
    Object.keys(roleRules).forEach(roleId => {
      roleRules[roleId] = roleRules[roleId].filter(id => ids.indexOf(id) < 0)
    })
    return delay({ status: 'ok' })
  }

  const rolePath = path.match(/^\/data-permission\/admin\/roles\/([^/]+)\/rules$/)
  if (rolePath && method === 'get') {
    const roleId = decodeURIComponent(rolePath[1])
    return delay(roleRules[roleId] || [])
  }

  if (rolePath && method === 'put') {
    const roleId = decodeURIComponent(rolePath[1])
    roleRules[roleId] = (data.ruleIds || []).slice()
    return delay({ status: 'ok' })
  }

  return Promise.reject(new Error('Mock API 未实现：' + method.toUpperCase() + ' ' + path))
}

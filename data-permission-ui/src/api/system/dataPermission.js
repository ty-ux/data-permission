import request from '@/utils/request'

// 规则管理
export function listRules(query) {
  return request({
    url: '/data-permission/admin/rules',
    method: 'get',
    params: query
  })
}

export function getRule(id) {
  return request({
    url: '/data-permission/admin/rules/' + id,
    method: 'get'
  })
}

export function saveRule(data) {
  return request({
    url: '/data-permission/admin/rules',
    method: 'post',
    data
  })
}

export function deleteRules(ids) {
  return request({
    url: '/data-permission/admin/rules/' + ids,
    method: 'delete'
  })
}

// 角色规则分配
export function listRoleRuleIds(roleId) {
  return request({
    url: '/data-permission/admin/roles/' + roleId + '/rules',
    method: 'get'
  })
}

export function assignRoleRules(roleId, ruleIds) {
  return request({
    url: '/data-permission/admin/roles/' + roleId + '/rules',
    method: 'put',
    data: { ruleIds }
  })
}

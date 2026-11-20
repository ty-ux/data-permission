import request from '@/utils/request'

// 与后端 data-permission.admin.path 保持一致，默认 /data-permission/admin
const BASE_URL = '/data-permission/admin'

// 规则管理
export function listRules(query) {
  return request({
    url: BASE_URL + '/rules',
    method: 'get',
    params: query
  })
}

export function getRule(id) {
  return request({
    url: BASE_URL + '/rules/' + id,
    method: 'get'
  })
}

export function saveRule(data) {
  return request({
    url: BASE_URL + '/rules',
    method: 'post',
    data
  })
}

export function deleteRules(ids) {
  return request({
    url: BASE_URL + '/rules/' + ids,
    method: 'delete'
  })
}

// 角色规则分配
export function listRoleRuleIds(roleId) {
  return request({
    url: BASE_URL + '/roles/' + roleId + '/rules',
    method: 'get'
  })
}

export function assignRoleRules(roleId, ruleIds) {
  return request({
    url: BASE_URL + '/roles/' + roleId + '/rules',
    method: 'put',
    data: { ruleIds }
  })
}

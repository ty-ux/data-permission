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
    url: BASE_URL + '/roles/' + encodeURIComponent(roleId) + '/rules',
    method: 'get'
  })
}

export function assignRoleRules(roleId, ruleIds) {
  return request({
    url: BASE_URL + '/roles/' + encodeURIComponent(roleId) + '/rules',
    method: 'put',
    data: { ruleIds }
  })
}

export function listPermissionRoles() {
  return request({ url: '/system/role/optionselect', method: 'get' }).then(({ data }) =>
    data.map(role => ({
      id: String(role.roleId),
      name: role.roleName,
      label: role.roleKey ? role.roleName + '（' + role.roleKey + '）' : role.roleName
    })))
}

export async function listRuleRoles(ruleId) {
  const roleIds = await request({
    url: BASE_URL + '/rules/' + encodeURIComponent(ruleId) + '/roles',
    method: 'get'
  })
  if (!Array.isArray(roleIds)) throw new Error('响应缺少角色绑定')
  if (!roleIds.length) return []

  // The bindings are authoritative. A missing or inaccessible directory only affects names.
  const names = await listPermissionRoles()
    .then(roles => new Map(roles.map(role => [role.id, role.name])))
    .catch(() => new Map())
  return roleIds.map(id => ({ id, name: names.get(String(id)) }))
}

export function validateRule(data) {
  return request({ url: BASE_URL + '/rules/validate', method: 'post', data })
}
export function subqueryFields() {
  return request({ url: BASE_URL + '/subquery-fields', method: 'get' })
}

// 人员与角色目录由宿主应用提供。默认适配 RuoYi-Vue 的只读接口。
export function searchTopologySubjects(type, keyword) {
  const isUser = type === 'user'
  const field = isUser ? 'userName' : 'roleName'
  return request({
    url: isUser ? '/system/user/list' : '/system/role/list',
    method: 'get',
    params: { [field]: keyword, pageNum: 1, pageSize: 20 }
  }).then(({ rows, total }) => ({
    total,
    options: rows.map(row => isUser
      ? {
        id: String(row.userId),
        name: row.nickName || row.userName,
        detail: row.userName,
        label: row.nickName ? row.nickName + '（' + row.userName + '）' : row.userName
      }
      : {
        id: String(row.roleId),
        name: row.roleName,
        detail: 'ID ' + row.roleId,
        label: row.roleName + '（ID ' + row.roleId + '）'
      })
  }))
}

export function listTopologyUserRoles(userId) {
  return request({
    url: '/system/user/' + encodeURIComponent(userId),
    method: 'get'
  }).then(({ roleIds, roles = [], data }) => {
    const assigned = data && data.roles
    const hasAssignedRoles = Array.isArray(assigned)
    if (!Array.isArray(roleIds) && !hasAssignedRoles) throw new Error('响应缺少人员角色')
    const names = new Map([...roles, ...(hasAssignedRoles ? assigned : [])]
      .map(role => [String(role.roleId), role.roleName]))
    const ids = Array.isArray(roleIds) ? roleIds : assigned.map(role => role.roleId)
    return [...new Set(ids.map(String))].map(id => ({
      id,
      name: names.get(id) || '角色 ' + id,
      detail: 'ID ' + id
    }))
  })
}

function tableKey(tableName) {
  // Match SqlConditionBuilder.normalize, including schema-qualified legacy rules.
  return (tableName || '').trim().replace(/[`"\[\]]/g, '').split('.').pop().toLowerCase()
}

export function buildPermissionTopology(roles, catalog) {
  const rulesById = new Map(catalog.map(rule => [String(rule.id), rule]))
  const sourcesByRule = new Map()
  let missingBindings = 0
  roles.forEach(role => {
    new Set(role.ruleIds.map(String)).forEach(id => {
      if (!rulesById.has(id)) {
        missingBindings++
        return
      }
      if (!sourcesByRule.has(id)) sourcesByRule.set(id, [])
      sourcesByRule.get(id).push(role)
    })
  })

  const tablesByKey = new Map()
  const rules = catalog.filter(rule => sourcesByRule.has(String(rule.id))).map(rule => {
    const id = String(rule.id)
    const table = tableKey(rule.tableName)
    const entry = { id, rule, roles: sourcesByRule.get(id), table }
    if (!tablesByKey.has(table)) tablesByKey.set(table, { id: table, rules: [] })
    tablesByKey.get(table).rules.push(entry)
    return entry
  })
  return { roles, rules, tables: [...tablesByKey.values()], missingBindings }
}

export function orderedTableRules(table, appPoint) {
  // Blank Mapper appPoint means no app-point filter. Global rules also match named points.
  return table.rules.filter(({ rule }) => rule.enabled &&
      (!appPoint || !rule.appPoint || rule.appPoint === appPoint))
    .sort((left, right) => (right.rule.priority || 0) - (left.rule.priority || 0) ||
      (left.rule.sortOrder || 0) - (right.rule.sortOrder || 0) ||
      (left.id < right.id ? -1 : Number(left.id > right.id)))
}

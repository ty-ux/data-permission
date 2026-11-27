import test from 'node:test'
import assert from 'node:assert/strict'
import { buildPermissionTopology, orderedTableRules } from './permissionTopology.js'

test('a shared rule has one node and keeps both source roles', () => {
  const roles = [
    { id: '2', ruleIds: [10, 10, 'missing'] },
    { id: '3', ruleIds: ['10', '11'] },
    { id: '4', ruleIds: [] }
  ]
  const graph = buildPermissionTopology(roles, [
    { id: '10', tableName: 'schema."Project"', enabled: true },
    { id: '11', tableName: '[project]', enabled: false },
    { id: 'unbound', tableName: 'contract', enabled: true }
  ])
  assert.equal(graph.roles.length, 3)
  assert.deepEqual(graph.rules.map(rule => rule.id), ['10', '11'])
  assert.deepEqual(graph.rules[0].roles.map(role => role.id), ['2', '3'])
  assert.equal(graph.missingBindings, 1)
  assert.deepEqual(graph.tables.map(table => table.id), ['project'])
  assert.deepEqual(graph.tables[0].rules.map(rule => rule.id), ['10', '11'])
})

test('table composition excludes disabled rules and follows engine order across roles', () => {
  const role = { id: '2', ruleIds: ['a', 'B', 'z', 'global', 'other', 'disabled'] }
  const rules = [
    { id: 'z', appPoint: 'edit', priority: 2, sortOrder: 2 },
    { id: 'a', appPoint: 'edit', priority: 2, sortOrder: 1 },
    { id: 'B', appPoint: 'edit', priority: 2, sortOrder: 1, logicOperator: 'OR' },
    { id: 'global', appPoint: null, priority: 5 },
    { id: 'other', appPoint: 'view', priority: 3 },
    { id: 'disabled', appPoint: '', priority: 10, enabled: false }
  ].map(rule => ({ tableName: 'project', enabled: true, ...rule }))
  const [table] = buildPermissionTopology([role], rules).tables
  assert.deepEqual(orderedTableRules(table, 'edit').map(rule => rule.id), ['global', 'B', 'a', 'z'])
  assert.deepEqual(orderedTableRules(table, '').map(rule => rule.id), ['global', 'other', 'B', 'a', 'z'])
  assert.deepEqual(orderedTableRules(table, 'unknown').map(rule => rule.id), ['global'])
  assert.equal(orderedTableRules(table, 'edit')[1].rule.logicOperator, 'OR')
})

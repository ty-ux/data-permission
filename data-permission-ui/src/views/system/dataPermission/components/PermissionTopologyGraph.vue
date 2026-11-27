<template>
  <div class="topology-scroll" tabindex="0" role="region" aria-label="权限关系图">
    <div class="topology-board" :class="{ 'has-user': user }" :style="{ '--lanes': lanes.length }">
      <div class="topology-headings" aria-hidden="true">
        <span v-for="lane in lanes" :key="lane.type">{{ lane.label }} <small>{{ lane.nodes.length }}</small></span>
      </div>
      <div class="topology-network" :style="{ height: height + 'px' }">
        <svg
          class="topology-lines"
          aria-hidden="true"
          :viewBox="'0 0 1000 ' + height"
          preserveAspectRatio="none"
        >
          <g
            v-for="edge in edges"
            :key="edge.from.key + '>' + edge.to.key"
            :class="{ active: edge.active, muted: selected && !edge.active, disabled: edge.disabled }"
          >
            <path :d="edge.path" vector-effect="non-scaling-stroke" />
            <path :d="edge.arrow" vector-effect="non-scaling-stroke" />
          </g>
        </svg>
        <ul v-for="lane in lanes" :key="lane.type" class="topology-lane" role="list" :aria-label="lane.label">
          <li
            v-for="node in lane.nodes"
            :key="node.key"
            class="topology-node"
            :class="[lane.type, { disabled: node.disabled }]"
            :style="{ top: node.y + 'px' }"
          >
            <div v-if="lane.type === 'user'" class="topology-card">
              <i class="el-icon-user" aria-hidden="true" />
              <span><strong>{{ node.name }}</strong><small>{{ node.detail }}</small></span>
            </div>
            <button
              v-else
              type="button"
              class="topology-card"
              :title="node.name"
              :aria-pressed="Boolean(selected && selected.key === node.key)"
              @click="$emit('select', node)"
            >
              <i :class="icons[lane.type]" aria-hidden="true" />
              <span><strong>{{ node.name }}</strong><small>{{ node.detail }}</small></span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script>
const ROW_HEIGHT = 74
const FIRST_ROW_Y = 45
// The cards occupy 78% of each column. Use the same proportion for line endpoints.
const CARD_HALF_WIDTH = 0.39

export default {
  name: 'PermissionTopologyGraph',
  props: {
    graph: { type: Object, required: true },
    user: { type: Object, default: null },
    selected: { type: Object, default: null }
  },
  data() {
    return { icons: { role: 'el-icon-key', rule: 'el-icon-document', table: 'el-icon-coin' } }
  },
  computed: {
    lanes() {
      const lanes = [
        { type: 'role', label: '角色', nodes: this.graph.roles.map(role => ({
          entry: role, name: role.name, detail: role.detail
        })) },
        { type: 'rule', label: '权限规则', nodes: this.graph.rules.map(entry => ({
          entry, name: entry.rule.ruleName || entry.id, disabled: !entry.rule.enabled,
          detail: (entry.rule.enabled ? '启用' : '停用') + ' · ' + (entry.rule.appPoint || '全部应用点')
        })) },
        { type: 'table', label: '目标表', nodes: this.graph.tables.map(table => ({
          entry: table, name: table.id || '未指定表', detail: table.rules.length + ' 条规则'
        })) }
      ]
      if (this.user) lanes.unshift({
        type: 'user', label: '人员', nodes: [{ entry: this.user, name: this.user.name, detail: this.user.detail }]
      })
      const rows = Math.max(...lanes.map(lane => lane.nodes.length))
      const width = 1000 / lanes.length
      return lanes.map((lane, column) => ({
        ...lane,
        nodes: lane.nodes.map((node, index) => ({
          ...node,
          type: lane.type,
          key: lane.type + ':' + node.entry.id,
          x: (column + 0.5) * width,
          y: FIRST_ROW_Y + ROW_HEIGHT * (index + (rows <= 5 ? (rows - lane.nodes.length) / 2 : 0))
        }))
      }))
    },
    height() {
      return Math.max(175, ...this.lanes.map(lane => lane.nodes.length * ROW_HEIGHT + 16))
    },
    edges() {
      const nodes = new Map(this.lanes.flatMap(lane => lane.nodes).map(node => [node.key, node]))
      const selected = this.selected
      const focusRules = new Set(!selected ? []
        : selected.type === 'role' ? selected.entry.ruleIds.map(String)
          : selected.type === 'table' ? selected.entry.rules.map(rule => rule.id) : [selected.entry.id])
      const focusRoles = selected && selected.type === 'role' ? new Set([selected.entry.id])
        : new Set(this.graph.rules.filter(rule => focusRules.has(rule.id)).flatMap(rule => rule.roles.map(role => role.id)))
      const links = []
      const halfWidth = 1000 * CARD_HALF_WIDTH / this.lanes.length
      const connect = (fromKey, toKey, active, disabled = false) => {
        const from = nodes.get(fromKey)
        const to = nodes.get(toKey)
        const start = from.x + halfWidth
        const end = to.x - halfWidth - 2
        const mid = (start + end) / 2
        links.push({
          from, to, active, disabled,
          path: `M ${start} ${from.y} C ${mid} ${from.y}, ${mid} ${to.y}, ${end} ${to.y}`,
          arrow: `M ${end - 5} ${to.y - 3} L ${end} ${to.y} L ${end - 5} ${to.y + 3}`
        })
      }
      if (this.user) this.graph.roles.forEach(role =>
        connect('user:' + this.user.id, 'role:' + role.id, focusRoles.has(role.id)))
      this.graph.rules.forEach(({ id, roles, table, rule }) => {
        const focused = focusRules.has(id)
        roles.forEach(role => connect('role:' + role.id, 'rule:' + id, focused && focusRoles.has(role.id), !rule.enabled))
        connect('rule:' + id, 'table:' + table, focused, !rule.enabled)
      })
      // Draw the selected path last so it stays visible at crossings.
      return links.sort((left, right) => Number(left.active) - Number(right.active))
    }
  }
}
</script>

<style scoped>
.topology-scroll {
  max-height: 482px;
  overflow: auto;
}

.topology-board {
  min-width: 575px;
}

.topology-board.has-user {
  min-width: 700px;
}

.topology-headings, .topology-network {
  display: grid;
  grid-template-columns: repeat(var(--lanes), minmax(0, 1fr));
}

.topology-headings {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #f2f6fc;
  border-bottom: 1px solid #dce5f0;
  padding: 12px 0;
  color: #283d58;
  font-weight: 600;
}

.topology-headings span {
  padding-inline: 11%;
}

.topology-headings small {
  margin-left: 3px;
  color: #526782;
  font-variant-numeric: tabular-nums;
}

.topology-network {
  position: relative;
  background: #fafcff radial-gradient(circle, #d8e3ef 1px, transparent 1px) 0 0 / 16px 16px;
}

.topology-lines {
  position: absolute;
  width: 100%;
  height: 100%;
  fill: none;
  stroke: #7992ad;
  stroke-width: 1.4;
  pointer-events: none;
}

.topology-lines .disabled path:first-child {
  stroke-dasharray: 4 3;
}

.topology-lines .muted {
  opacity: .28;
}

.topology-lines .active {
  stroke: #1764b3;
  stroke-width: 2;
}

.topology-lane {
  position: relative;
  margin: 0;
  padding: 0;
  list-style: none;
}

.topology-node {
  position: absolute;
  left: 11%;
  width: 78%;
  transform: translateY(-50%);
  --node-tone: #38618c;
}

.topology-node.rule {
  --node-tone: #127267;
}

.topology-node.disabled {
  --node-tone: #86612a;
}

.topology-node.table {
  --node-tone: #575282;
}

.topology-card {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 56px;
  padding: 8px 9px;
  text-align: left;
  font: inherit;
  color: #1d3048;
  background: #fff;
  border: 1px solid #cbd9e8;
  border-left: 3px solid var(--node-tone);
  border-radius: 6px;
}

button.topology-card {
  cursor: pointer;
}

.topology-card:hover, .topology-card[aria-pressed="true"] {
  border-color: var(--node-tone);
  background: #f1f7ff;
}

.topology-card:focus-visible, .topology-scroll:focus-visible {
  outline: 2px solid #1764b3;
  outline-offset: -2px;
}

.topology-card i {
  color: var(--node-tone);
  font-size: 16px;
}

.topology-card span {
  min-width: 0;
}

.topology-card strong, .topology-card small {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.topology-card strong {
  font-size: 14px;
}

.topology-card small {
  color: #526782;
  margin-top: 4px;
}
</style>

<template>
  <section class="topology-page">
    <header class="topology-header">
      <div>
        <h2 ref="heading" tabindex="-1">权限拓扑</h2>
        <p>从人员或角色出发，查看规则的来源、可见范围和目标表。</p>
      </div>
      <el-button size="small" icon="el-icon-back" @click="$emit('back')">返回规则列表</el-button>
    </header>

    <form class="topology-query" @submit.prevent="search">
      <fieldset>
        <legend>查看对象</legend>
        <label><input v-model="type" type="radio" value="user"> 人员</label>
        <label><input v-model="type" type="radio" value="role"> 角色</label>
      </fieldset>
      <div class="topology-filter">
        <label for="topology-keyword">{{ isUser ? '人员账号' : '角色名称' }}</label>
        <div class="topology-search">
          <input
            id="topology-keyword"
            v-model.trim="keyword"
            type="search"
            :placeholder="isUser ? '输入登录账号' : '输入角色名称'"
          >
          <el-button size="small" native-type="submit" :loading="searching">搜索</el-button>
        </div>
      </div>
      <div class="topology-filter">
        <label for="topology-subject">{{ isUser ? '选择人员' : '选择角色' }}</label>
        <select id="topology-subject" v-model="subjectId" :disabled="searching || !options.length" @change="load">
          <option value="">{{ isUser ? '请选择人员' : '请选择角色' }}</option>
          <option v-for="option in options" :key="option.id" :value="option.id">{{ option.label }}</option>
        </select>
      </div>
    </form>
    <p v-if="searchNote" class="topology-note" role="status">{{ searchNote }}</p>

    <div v-if="error" class="topology-message" role="alert">
      {{ error }}
      <el-button v-if="subjectId" size="small" type="text" @click="load">重新加载</el-button>
    </div>
    <div v-else-if="loading" class="topology-message">正在加载权限关系…</div>
    <div v-else-if="!graph" class="topology-message">选择人员或角色后，即可查看权限关系。</div>
    <template v-else>
      <div class="topology-summary">
        <h3>{{ subject.name }}的权限关系</h3>
        <p role="status">{{ graph.roles.length }} 个角色 · {{ enabledCount }} 条启用 · {{ graph.rules.length - enabledCount }} 条停用</p>
      </div>
      <p v-if="!graph.roles.length" class="topology-note">该人员没有分配角色。</p>
      <p v-else-if="!graph.rules.length" class="topology-note">当前没有可展示的已绑定规则。</p>
      <p v-if="graph.missingBindings" class="topology-note" role="status">
        有 {{ graph.missingBindings }} 个规则绑定在规则列表中找不到对应记录。
      </p>
      <div class="topology-workspace">
        <permission-topology-graph :graph="graph" :user="isUser ? subject : null" :selected="selected" @select="selectNode" />
        <section class="topology-inspector">
          <h3>{{ selected ? selected.name : '节点详情' }}</h3>
          <p v-if="!selected">选择图中的角色、规则或目标表查看详情。虚线表示规则已停用。</p>
          <template v-else-if="selected.type === 'role'">
            <p>{{ selected.detail }} · 已绑定 {{ roleRules.length }} 条可查看的规则</p>
            <ul v-if="roleRules.length">
              <li v-for="{ id, rule } in roleRules" :key="id">{{ rule.ruleName || id }}（{{ rule.enabled ? '启用' : '停用' }}）</li>
            </ul>
          </template>
          <template v-else-if="selected.type === 'rule'">
            <p>{{ selected.detail }}</p>
            <dl>
              <dt>可见范围</dt>
              <dd class="topology-condition">{{ describeRule(selectedRule) }}</dd>
              <dt>来源角色</dt>
              <dd>{{ selected.entry.roles.map(role => role.name).join('、') }}</dd>
              <dt>目标表</dt>
              <dd>{{ selectedRule.tableName || '未指定表' }}</dd>
              <dt>组合配置</dt>
              <dd>优先级 {{ selectedRule.priority || 0 }}，排序 {{ selectedRule.sortOrder || 0 }}，与前序结果「{{ selectedRule.logicOperator === 'OR' ? '或' : '且' }}」</dd>
              <dt v-if="selectedRule.description">备注</dt>
              <dd v-if="selectedRule.description">{{ selectedRule.description }}</dd>
            </dl>
            <details>
              <summary>查看原始条件</summary>
              <ul v-if="selectedRule.ruleMode === 'COMPLEX'">
                <li v-for="(item, index) in selectedRule.items" :key="item.id || index">
                  <code>{{ item.leftExpr }} {{ item.operator }} {{ item.rightExpr }}</code>
                  <small>{{ item.rightType }} · 分组 {{ item.groupNo }} / {{ item.subGroupNo }} · 顺序 {{ item.sortOrder }} · {{ item.logicOperator }}</small>
                </li>
              </ul>
              <p v-else><code>{{ selectedRule.fieldName }} {{ selectedRule.conditionType }} {{ selectedRule.conditionValue }}</code></p>
            </details>
          </template>
          <template v-else>
            <label for="topology-app-point">按应用点查看组合</label>
            <select id="topology-app-point" v-model="appPoint">
              <option value="">未指定应用点</option>
              <option v-for="point in tableAppPoints" :key="point" :value="point">{{ point }}</option>
            </select>
            <p>{{ appPoint ? '包含适用于全部应用点的规则。' : '未指定应用点时，匹配该表的所有应用点。' }}</p>
            <p v-if="!tableRules.length">该表在此应用点下没有已启用的规则。</p>
            <template v-else>
              <p>按下列顺序组合，每一步与前面已组合的结果计算。</p>
              <ol>
                <li v-for="({ id, rule }, index) in tableRules" :key="id">
                  <strong v-if="index">{{ rule.logicOperator === 'OR' ? '或' : '且' }} </strong>{{ rule.ruleName || id }}
                </li>
              </ol>
            </template>
          </template>
        </section>
      </div>
      <p class="topology-note">图中展示已配置的规则。实际可见范围取决于查询应用点、动态取值和运行策略。</p>
    </template>
  </section>
</template>

<script>
import { listRoleRuleIds, listRules, listTopologyUserRoles, searchTopologySubjects } from '@/api/system/dataPermission'
import { buildPermissionTopology, orderedTableRules } from '../permissionTopology'
import { describeRule } from '../rulePresentation'
import PermissionTopologyGraph from './PermissionTopologyGraph.vue'

export default {
  name: 'PermissionTopology',
  components: { PermissionTopologyGraph },
  data() {
    return {
      type: 'user',
      keyword: '',
      options: [],
      subjectId: '',
      searchNote: '',
      searching: false,
      searchSeq: 0,
      loadSeq: 0,
      loading: false,
      error: '',
      graph: null,
      selected: null,
      appPoint: ''
    }
  },
  computed: {
    isUser() {
      return this.type === 'user'
    },
    subject() {
      return this.options.find(option => option.id === this.subjectId)
    },
    enabledCount() {
      return this.graph.rules.filter(({ rule }) => rule.enabled).length
    },
    selectedRule() {
      return this.selected.entry.rule
    },
    roleRules() {
      return this.graph.rules.filter(({ roles }) => roles.some(role => role.id === this.selected.entry.id))
    },
    tableAppPoints() {
      return [...new Set(this.selected.entry.rules.map(({ rule }) => rule.appPoint).filter(Boolean))].sort()
    },
    tableRules() {
      return orderedTableRules(this.selected.entry, this.appPoint)
    }
  },
  watch: {
    type() {
      this.keyword = ''
      this.search()
    }
  },
  mounted() {
    this.$refs.heading.focus()
    this.search()
  },
  beforeDestroy() {
    this.searchSeq++
    this.loadSeq++
  },
  methods: {
    describeRule,
    async search() {
      const seq = ++this.searchSeq
      this.loadSeq++
      this.loading = false
      this.error = ''
      this.graph = null
      this.selected = null
      this.subjectId = ''
      this.options = []
      this.searchNote = ''
      this.searching = true
      try {
        const { options, total } = await searchTopologySubjects(this.type, this.keyword)
        if (seq !== this.searchSeq) return
        this.options = options
        if (!options.length) this.searchNote = '未找到匹配的' + (this.isUser ? '人员' : '角色') + '，可调整关键词后重试。'
        else if (total > options.length) this.searchNote = '显示前 ' + options.length + ' 项，可输入更具体的关键词。'
      } catch {
        if (seq === this.searchSeq) this.error = '目录加载失败。请确认具备查看权限，或重新搜索。'
      } finally {
        if (seq === this.searchSeq) this.searching = false
      }
    },
    async load() {
      const seq = ++this.loadSeq
      const subject = this.subject
      this.graph = null
      this.selected = null
      this.error = ''
      this.loading = Boolean(subject)
      if (!subject) return
      try {
        const roles = this.isUser ? await listTopologyUserRoles(subject.id) : [subject]
        if (seq !== this.loadSeq) return
        const [bindings, catalog] = roles.length ? await Promise.all([
          Promise.all(roles.map(async role => ({ ...role, ruleIds: await listRoleRuleIds(role.id) }))),
          listRules({})
        ]) : [[], []]
        if (seq === this.loadSeq) this.graph = buildPermissionTopology(bindings, catalog)
      } catch {
        if (seq === this.loadSeq) this.error = '权限关系加载失败，请确认有权查看该对象及数据权限配置。'
      } finally {
        if (seq === this.loadSeq) this.loading = false
      }
    },
    selectNode(node) {
      this.selected = this.selected && this.selected.key === node.key ? null : node
      this.appPoint = ''
    }
  }
}
</script>

<style scoped>
.topology-page {
  color: #1d3048;
}

.topology-header, .topology-summary, .topology-query, .topology-search {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px 20px;
}

.topology-header, .topology-summary {
  justify-content: space-between;
}

.topology-header h2, .topology-summary h3, .topology-inspector h3 {
  margin: 0;
}

.topology-header h2 {
  font-size: 24px;
}

.topology-header p, .topology-summary p, .topology-note, .topology-inspector p {
  color: #526782;
  line-height: 1.6;
}

.topology-header p {
  margin: 7px 0 0;
}

.topology-query {
  align-items: flex-end;
  margin: 20px 0 12px;
  padding: 14px 16px;
  background: #f2f6fc;
  border: 1px solid #dce5f0;
  border-radius: 8px;
}

.topology-query fieldset {
  padding: 0;
  margin: 0;
  border: 0;
  min-width: 0;
}

.topology-query legend, .topology-filter > label, .topology-inspector > label {
  display: block;
  margin: 0 0 5px;
  padding: 0;
  font-size: 13px;
  font-weight: 600;
}

.topology-query fieldset label {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  margin-right: 6px;
  gap: 4px;
}

.topology-search {
  gap: 6px;
}

.topology-filter {
  min-width: 0;
}

.topology-filter input, .topology-filter select, .topology-inspector select {
  box-sizing: border-box;
  width: 200px;
  max-width: 100%;
  min-height: 34px;
  padding: 5px 8px;
  border: 1px solid #a4b6c9;
  border-radius: 4px;
  background: #fff;
  color: inherit;
  font: inherit;
}

.topology-inspector select {
  width: 100%;
}

.topology-note {
  font-size: 13px;
}

.topology-message {
  display: grid;
  place-content: center;
  min-height: 160px;
  padding: 16px;
  color: #526782;
  border: 1px dashed #a4b6c9;
  border-radius: 8px;
}

.topology-summary {
  margin: 16px 0 10px;
}

.topology-summary p {
  margin: 0;
}

.topology-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  border: 1px solid #dce5f0;
  border-radius: 8px;
  overflow: hidden;
}

.topology-inspector {
  padding: 16px;
  border-top: 1px solid #dce5f0;
  background: #fff;
  overflow-wrap: anywhere;
}

.topology-inspector h3 {
  margin-bottom: 10px;
}

.topology-inspector dt {
  font-weight: 600;
  margin-top: 12px;
}

.topology-inspector dd {
  margin: 5px 0 0;
  line-height: 1.65;
}

.topology-inspector ul, .topology-inspector ol {
  padding-left: 20px;
  line-height: 1.75;
}

.topology-inspector details {
  margin-top: 16px;
}

.topology-inspector summary {
  cursor: pointer;
}

.topology-inspector code {
  white-space: pre-wrap;
}

.topology-inspector small {
  display: block;
  color: #526782;
}

.topology-condition {
  background: #edf7f4;
  border-left: 3px solid #127267;
  padding: 8px 10px;
}

@media (min-width: 1240px) {
  .topology-workspace {
    grid-template-columns: minmax(0, 1fr) minmax(265px, 26%);
  }

  .topology-inspector {
    align-self: stretch;
    border-top: 0;
    border-left: 1px solid #dce5f0;
  }
}
</style>

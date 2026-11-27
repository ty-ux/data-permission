<template>
  <el-dialog
    ref="dialog"
    title="已分配角色"
    :visible.sync="visible"
    width="min(600px, calc(100% - 32px))"
    top="10vh"
    append-to-body
    :close-on-click-modal="false"
    @open="focusDialog"
    @closed="restoreFocus"
    @keydown.tab.native="keepFocusInDialog"
  >
    <p v-if="rule" class="dp-rule-roles-rule">
      <span>规则</span>
      <strong>{{ rule.ruleName || rule.id }}</strong>
      <el-tag v-if="!rule.enabled" type="info" size="mini">规则已停用</el-tag>
    </p>
    <div v-if="failed">
      <p role="alert">角色绑定加载失败，请重试。</p>
      <el-button size="small" @click="load">重新加载</el-button>
    </div>
    <div
      v-else
      v-loading="loading"
      class="dp-rule-roles-table"
      role="region"
      aria-label="已分配角色列表"
      :aria-busy="loading"
      tabindex="0"
    >
      <el-table
        :data="roles"
        :empty-text="loading ? '正在查询已绑定的角色…' : '这条规则还未分配给任何角色。'"
        row-key="id"
        size="small"
        border
        stripe
      >
        <el-table-column label="角色名称" prop="name">
          <template slot-scope="{ row }">{{ row.name || '角色名称不可用' }}</template>
        </el-table-column>
        <el-table-column label="角色 ID" prop="id" width="128" />
      </el-table>
    </div>
    <span slot="footer">
      <el-button ref="closeButton" size="small" @click="visible = false">关闭</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { listRuleRoles } from '@/api/system/dataPermission'

export default {
  name: 'RuleRolesDialog',
  data() {
    return { visible: false, trigger: null, rule: null, roles: [], loading: false, failed: false, requestSeq: 0 }
  },
  beforeDestroy() {
    this.requestSeq++
  },
  methods: {
    open(rule, trigger) {
      this.rule = rule
      this.trigger = trigger
      this.visible = true
      this.load()
    },
    focusDialog() {
      this.$nextTick(() => {
        if (this.visible) this.$refs.closeButton.$el.focus()
      })
    },
    restoreFocus() {
      if (this.visible) return
      if (this.trigger && this.trigger.isConnected) this.trigger.focus()
      this.trigger = null
    },
    keepFocusInDialog(event) {
      const controls = this.$refs.dialog.$el.querySelectorAll('button:not([disabled]), .dp-rule-roles-table')
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey ? event.target === first : event.target === last) {
        const next = event.shiftKey ? last : first
        event.preventDefault()
        next.focus()
      }
    },
    async load() {
      const seq = ++this.requestSeq
      this.loading = true
      this.failed = false
      this.roles = []
      try {
        const roles = await listRuleRoles(this.rule.id)
        if (seq === this.requestSeq) this.roles = roles
      } catch {
        if (seq === this.requestSeq) this.failed = true
      } finally {
        if (seq === this.requestSeq) this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.dp-rule-roles-rule {
  display: flex;
  flex-wrap: wrap;
  margin: 0 0 16px;
  align-items: center;
  gap: 8px 12px;
  overflow-wrap: anywhere;
}

.dp-rule-roles-table {
  max-height: min(340px, 45vh);
  overflow: auto;
}

.dp-rule-roles-table ::v-deep .el-table {
  overflow: visible;
}

.dp-rule-roles-table ::v-deep .el-table__header-wrapper {
  position: sticky;
  top: 0;
  z-index: 1;
}
</style>

<template>
  <dialog ref="dialog" class="dp-rule-roles" aria-labelledby="dp-rule-roles-title">
    <h1 id="dp-rule-roles-title">已分配角色</h1>
    <p v-if="rule" class="dp-rule-roles-rule">
      <strong>{{ rule.ruleName || rule.id }}</strong>
      <el-tag v-if="!rule.enabled" type="info" size="mini">规则已停用</el-tag>
    </p>
    <p v-if="loading" role="status">正在查询已绑定的角色…</p>
    <div v-else-if="failed">
      <p role="alert">角色绑定加载失败，请重试。</p>
      <el-button size="small" @click="load">重新加载</el-button>
    </div>
    <ul v-else-if="roles.length" class="dp-rule-roles-list">
      <li v-for="role in roles" :key="role.id">
        <span>{{ role.name || '角色名称不可用' }}</span>
        <code>ID {{ role.id }}</code>
      </li>
    </ul>
    <p v-else>这条规则还未分配给任何角色。</p>
    <form method="dialog" class="dp-rule-roles-footer">
      <button class="el-button el-button--default el-button--small" autofocus>关闭</button>
    </form>
  </dialog>
</template>

<script>
import { listRuleRoles } from '@/api/system/dataPermission'

export default {
  name: 'RuleRolesDialog',
  data() {
    return { rule: null, roles: [], loading: false, failed: false, requestSeq: 0 }
  },
  beforeDestroy() {
    this.requestSeq++
  },
  methods: {
    open(rule) {
      this.rule = rule
      this.$refs.dialog.showModal()
      this.load()
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
.dp-rule-roles {
  box-sizing: border-box;
  width: min(440px, calc(100% - 32px));
  max-height: calc(100% - 32px);
  padding: 20px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  color: #303133;
  overflow-wrap: anywhere;
}

.dp-rule-roles:modal {
  overscroll-behavior: contain;
}

.dp-rule-roles::backdrop {
  background: rgb(0 0 0 / 45%);
  overflow: hidden;
  overscroll-behavior: contain;
}

.dp-rule-roles h1 {
  margin: 0;
  font-size: 18px;
}

.dp-rule-roles-rule, .dp-rule-roles-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.dp-rule-roles-list {
  margin: 16px 0;
  padding-left: 20px;
}

.dp-rule-roles-list li {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}

.dp-rule-roles-list code {
  color: #606266;
}

.dp-rule-roles-footer {
  margin-top: 16px;
  text-align: right;
}
</style>

<template>
  <el-dialog
    title="角色规则分配"
    :visible.sync="visible"
    width="640px"
    append-to-body
    :close-on-click-modal="false"
    @closed="reset"
  >
    <el-form label-width="90px" size="small">
      <el-form-item label="角色 ID">
        <el-input
          v-model.trim="roleId"
          placeholder="如：2"
          style="width: 220px"
          @keyup.enter.native="loadAssigned"
        />
        <el-button type="primary" size="mini" style="margin-left: 8px" :loading="loading" @click="loadAssigned">
          加载
        </el-button>
      </el-form-item>
      <el-form-item label="绑定规则">
        <el-select
          v-model="assignedRuleIds"
          multiple
          filterable
          collapse-tags
          :loading="loading"
          :disabled="loadedRoleId !== roleId || !roleId"
          style="width: 100%"
          placeholder="请选择该角色可用的规则"
        >
          <el-option v-for="rule in allRules" :key="rule.id" :label="ruleLabel(rule)" :value="rule.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="dp-hint">
      保存会覆盖该角色现有的规则绑定；角色 ID 对应 <code>sys_role.role_id</code>。进入页面后先填写角色 ID 并点击“加载”。
    </div>
    <span slot="footer">
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { listRules, listRoleRuleIds, assignRoleRules } from '@/api/system/dataPermission'

export default {
  name: 'RoleRulesDialog',
  data() {
    return {
      visible: false,
      loading: false,
      saving: false,
      roleId: '',
      loadedRoleId: null,
      assignedRuleIds: [],
      allRules: []
    }
  },
  methods: {
    open() {
      this.visible = true
      this.loading = true
      this.allRules = []
      listRules({})
        .then(res => {
          this.allRules = res || []
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false
        })
    },
    loadAssigned() {
      if (!this.roleId) {
        this.$message.error('请输入角色 ID')
        return
      }
      this.loading = true
      listRoleRuleIds(this.roleId)
        .then(res => {
          this.assignedRuleIds = res || []
          this.loadedRoleId = this.roleId
        })
        .catch(() => {})
        .finally(() => {
          this.loading = false
        })
    },
    submit() {
      if (!this.roleId) {
        this.$message.error('请输入角色 ID')
        return
      }
      if (this.loadedRoleId !== this.roleId) {
        this.$message.error('请先加载该角色当前的规则')
        return
      }
      this.saving = true
      assignRoleRules(this.roleId, this.assignedRuleIds)
        .then(() => {
          this.$message.success('保存成功')
          this.visible = false
          this.$emit('saved')
        })
        .catch(() => {})
        .finally(() => {
          this.saving = false
        })
    },
    ruleLabel(rule) {
      const appPoint = rule.appPoint ? ' / ' + rule.appPoint : ''
      return (rule.ruleName || rule.id) + '（' + (rule.tableName || '-') + appPoint + '）'
    },
    reset() {
      this.roleId = ''
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.allRules = []
      this.loading = false
      this.saving = false
    }
  }
}
</script>

<style scoped>
.dp-hint {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
  margin-top: 4px;
}
</style>

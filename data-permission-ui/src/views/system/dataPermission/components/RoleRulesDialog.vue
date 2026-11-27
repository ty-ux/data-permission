<template>
  <el-dialog
    title="角色规则分配"
    :visible.sync="visible"
    width="640px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!saving"
    :before-close="handleClose"
    @closed="reset"
  >
    <el-form label-width="90px" size="small" :disabled="saving" @submit.native.prevent>
      <el-form-item label="角色" for="dp-assignment-role">
        <el-select
          id="dp-assignment-role"
          v-model="roleId"
          filterable
          clearable
          :loading="loadingOptions"
          :disabled="optionsFailed"
          style="width: 100%"
          placeholder="按角色名称或编码搜索"
          @change="loadAssigned"
        >
          <el-option v-for="role in roles" :key="role.id" :value="role.id" :label="role.label" />
        </el-select>
        <div v-if="optionsFailed || bindingsFailed" role="alert">
          {{ optionsFailed ? '角色或规则列表加载失败。' : '角色绑定加载失败。' }}
          <el-button type="text" size="small" @click="optionsFailed ? loadOptions() : loadAssigned()">重新加载</el-button>
        </div>
      </el-form-item>
      <el-form-item label="绑定规则" for="dp-assignment-rules">
        <el-select
          id="dp-assignment-rules"
          v-model="assignedRuleIds"
          multiple
          filterable
          collapse-tags
          :loading="loading"
          :disabled="saving || loadedRoleId !== roleId || !roleId"
          style="width: 100%"
          placeholder="请选择该角色可用的规则"
        >
          <el-option v-for="rule in allRules" :key="rule.id" :label="ruleLabel(rule)" :value="rule.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="dp-hint">
      选择角色后会自动加载已绑定的规则。保存会覆盖该角色现有的规则绑定。
    </div>
    <span slot="footer">
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!loadedRoleId || loadedRoleId !== roleId" @click="submit">保存</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { listRules, listPermissionRoles, listRoleRuleIds, assignRoleRules } from '@/api/system/dataPermission'

export default {
  name: 'RoleRulesDialog',
  data() {
    return {
      visible: false,
      loading: false,
      loadingOptions: false,
      optionsFailed: false,
      bindingsFailed: false,
      saving: false,
      session: 0,
      loadSeq: 0,
      roleId: '',
      loadedRoleId: null,
      assignedRuleIds: [],
      allRules: [],
      roles: []
    }
  },
  methods: {
    open() {
      this.reset()
      this.visible = true
      this.loadOptions()
    },
    async loadOptions() {
      const token = ++this.session
      this.loadingOptions = true
      this.optionsFailed = false
      try {
        const [roles, rules] = await Promise.all([listPermissionRoles(), listRules({})])
        if (token !== this.session) return
        this.roles = roles
        this.allRules = rules || []
      } catch {
        if (token === this.session) this.optionsFailed = true
      } finally {
        if (token === this.session) this.loadingOptions = false
      }
    },
    handleClose(done) {
      if (this.saving) {
        return
      }
      done()
    },
    async loadAssigned() {
      const roleId = this.roleId
      const seq = ++this.loadSeq
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.loading = Boolean(roleId)
      this.bindingsFailed = false
      if (!roleId) return
      try {
        const ruleIds = await listRoleRuleIds(roleId)
        if (seq !== this.loadSeq) return
        this.assignedRuleIds = ruleIds || []
        this.loadedRoleId = roleId
      } catch {
        if (seq === this.loadSeq) this.bindingsFailed = true
      } finally {
        if (seq === this.loadSeq) this.loading = false
      }
    },
    submit() {
      const roleId = this.roleId
      if (!roleId || this.loadedRoleId !== roleId) {
        return
      }
      const ruleIds = this.assignedRuleIds.slice()
      const assignedRules = this.allRules.filter(rule => ruleIds.indexOf(rule.id) >= 0)
      const enabledCount = assignedRules.filter(rule => rule.enabled).length
      const disabledCount = assignedRules.length - enabledCount
      if (!enabledCount) {
        this.$confirm('该角色当前没有绑定任何启用规则，保存后该角色将看不到受管数据表的数据。确认继续？', '警告', {
          confirmButtonText: '确认保存',
          cancelButtonText: '取消',
          type: 'warning'
        })
          .then(() => this.save(roleId, ruleIds, enabledCount, disabledCount))
          .catch(() => {})
        return
      }
      this.save(roleId, ruleIds, enabledCount, disabledCount)
    },
    save(roleId, ruleIds, enabledCount, disabledCount) {
      const token = this.session
      this.saving = true
      assignRoleRules(roleId, ruleIds)
        .then(() => {
          if (token !== this.session) {
            return
          }
          const disabledTip = disabledCount ? '，另有 ' + disabledCount + ' 条停用规则' : ''
          const role = this.roles.find(option => option.id === roleId)
          this.$message.success('保存成功：' + role.name + '绑定 ' + enabledCount + ' 条启用规则' + disabledTip)
          this.visible = false
          this.$emit('saved')
        })
        .catch(() => {})
        .finally(() => {
          if (token === this.session) {
            this.saving = false
          }
        })
    },
    ruleLabel(rule) {
      const appPoint = rule.appPoint ? ' / ' + rule.appPoint : ''
      const disabled = rule.enabled ? '' : '[停用] '
      return disabled + (rule.ruleName || rule.id) + '（' + (rule.tableName || '-') + appPoint + '）'
    },
    reset() {
      if (this.visible) return
      this.session++
      this.loadSeq++
      this.roleId = ''
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.allRules = []
      this.roles = []
      this.loading = false
      this.loadingOptions = false
      this.optionsFailed = false
      this.bindingsFailed = false
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

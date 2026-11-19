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
    <el-form label-width="90px" size="small" :disabled="saving">
      <el-form-item label="角色 ID">
        <el-input
          v-model.trim="roleId"
          placeholder="如：2"
          style="width: 220px"
          @input="handleRoleIdChange"
          @keyup.enter.native="loadAssigned"
        />
        <el-button type="primary" size="mini" style="margin-left: 8px" :loading="loading" :disabled="saving" @click="loadAssigned">
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
          :disabled="saving || loadedRoleId !== roleId || !roleId"
          style="width: 100%"
          placeholder="请选择该角色可用的规则"
        >
          <el-option v-for="rule in allRules" :key="rule.id" :label="ruleLabel(rule)" :value="rule.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="dp-hint">
      保存会覆盖该角色现有的规则绑定；角色 ID 对应 <code>sys_role.role_id</code>。进入页面后先填写角色 ID 并点击“加载”。绑定的规则全部停用时，该角色将看不到受管数据表的数据。
    </div>
    <span slot="footer">
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
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
      session: 0,
      loadSeq: 0,
      roleId: '',
      loadedRoleId: null,
      assignedRuleIds: [],
      allRules: []
    }
  },
  methods: {
    open() {
      const token = ++this.session
      this.visible = true
      this.roleId = ''
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.allRules = []
      this.loading = true
      listRules({})
        .then(res => {
          if (token !== this.session) {
            return
          }
          this.allRules = res || []
        })
        .catch(() => {
          if (token === this.session) {
            this.$message.error('规则列表加载失败')
          }
        })
        .finally(() => {
          if (token === this.session) {
            this.loading = false
          }
        })
    },
    handleClose(done) {
      if (this.saving) {
        return
      }
      done()
    },
    handleRoleIdChange() {
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.loading = false
      this.loadSeq++
    },
    isValidRoleId(roleId) {
      return /^\d+$/.test(String(roleId || '').trim())
    },
    loadAssigned() {
      const roleId = String(this.roleId || '').trim()
      if (!roleId) {
        this.$message.error('请输入角色 ID')
        return
      }
      if (!this.isValidRoleId(roleId)) {
        this.$message.error('角色 ID 必须是数字')
        return
      }
      const seq = ++this.loadSeq
      this.loading = true
      listRoleRuleIds(roleId)
        .then(res => {
          if (seq !== this.loadSeq || roleId !== String(this.roleId || '').trim()) {
            return
          }
          this.assignedRuleIds = res || []
          this.loadedRoleId = roleId
        })
        .catch(() => {})
        .finally(() => {
          if (seq === this.loadSeq) {
            this.loading = false
          }
        })
    },
    submit() {
      const roleId = String(this.roleId || '').trim()
      if (!roleId) {
        this.$message.error('请输入角色 ID')
        return
      }
      if (!this.isValidRoleId(roleId)) {
        this.$message.error('角色 ID 必须是数字')
        return
      }
      if (this.loadedRoleId !== roleId) {
        this.$message.error('请先加载该角色当前的规则')
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
          this.$message.success('保存成功：角色 ' + roleId + ' 绑定 ' + enabledCount + ' 条启用规则' + disabledTip)
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
      this.roleId = ''
      this.loadedRoleId = null
      this.assignedRuleIds = []
      this.allRules = []
      this.loading = false
      this.saving = false
      this.loadSeq++
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

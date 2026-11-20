<template>
  <el-dialog
    v-model="visible"
    title="角色规则分配"
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
          v-model="roleId"
          placeholder="如：2"
          style="width: 220px"
          @input="handleRoleIdChange"
          @keyup.enter="loadAssigned"
        />
        <el-button type="primary" size="small" style="margin-left: 8px" :loading="loading" :disabled="saving" @click="loadAssigned">
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
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignRoleRules, listRoleRuleIds, listRules } from '@/api/system/dataPermission'

const emit = defineEmits(['saved'])

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const roleId = ref('')
const loadedRoleId = ref(null)
const assignedRuleIds = ref([])
const allRules = ref([])

let session = 0
let loadSeq = 0

function open() {
  const token = ++session
  visible.value = true
  roleId.value = ''
  loadedRoleId.value = null
  assignedRuleIds.value = []
  allRules.value = []
  loading.value = true
  listRules({})
    .then(res => {
      if (token !== session) {
        return
      }
      allRules.value = res || []
    })
    .catch(() => {
      if (token === session) {
        ElMessage.error('规则列表加载失败')
      }
    })
    .finally(() => {
      if (token === session) {
        loading.value = false
      }
    })
}

function handleClose(done) {
  if (saving.value) {
    return
  }
  done()
}

function handleRoleIdChange() {
  roleId.value = String(roleId.value || '').trim()
  loadedRoleId.value = null
  assignedRuleIds.value = []
  loading.value = false
  loadSeq++
}

function isValidRoleId(value) {
  return /^\d+$/.test(String(value || '').trim())
}

function loadAssigned() {
  const value = String(roleId.value || '').trim()
  if (!value) {
    ElMessage.error('请输入角色 ID')
    return
  }
  if (!isValidRoleId(value)) {
    ElMessage.error('角色 ID 必须是数字')
    return
  }
  const seq = ++loadSeq
  loading.value = true
  listRoleRuleIds(value)
    .then(res => {
      if (seq !== loadSeq || value !== String(roleId.value || '').trim()) {
        return
      }
      assignedRuleIds.value = res || []
      loadedRoleId.value = value
    })
    .catch(() => {})
    .finally(() => {
      if (seq === loadSeq) {
        loading.value = false
      }
    })
}

function submit() {
  const value = String(roleId.value || '').trim()
  if (!value) {
    ElMessage.error('请输入角色 ID')
    return
  }
  if (!isValidRoleId(value)) {
    ElMessage.error('角色 ID 必须是数字')
    return
  }
  if (loadedRoleId.value !== value) {
    ElMessage.error('请先加载该角色当前的规则')
    return
  }
  const ruleIds = assignedRuleIds.value.slice()
  const assignedRules = allRules.value.filter(rule => ruleIds.indexOf(rule.id) >= 0)
  const enabledCount = assignedRules.filter(rule => rule.enabled).length
  const disabledCount = assignedRules.length - enabledCount
  if (!enabledCount) {
    ElMessageBox.confirm(
      '该角色当前没有绑定任何启用规则，保存后该角色将看不到受管数据表的数据。确认继续？',
      '警告',
      {
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
      .then(() => save(value, ruleIds, enabledCount, disabledCount))
      .catch(() => {})
    return
  }
  save(value, ruleIds, enabledCount, disabledCount)
}

function save(value, ruleIds, enabledCount, disabledCount) {
  const token = session
  saving.value = true
  assignRoleRules(value, ruleIds)
    .then(() => {
      if (token !== session) {
        return
      }
      const disabledTip = disabledCount ? '，另有 ' + disabledCount + ' 条停用规则' : ''
      ElMessage.success('保存成功：角色 ' + value + ' 绑定 ' + enabledCount + ' 条启用规则' + disabledTip)
      visible.value = false
      emit('saved')
    })
    .catch(() => {})
    .finally(() => {
      if (token === session) {
        saving.value = false
      }
    })
}

function ruleLabel(rule) {
  const appPoint = rule.appPoint ? ' / ' + rule.appPoint : ''
  const disabled = rule.enabled ? '' : '[停用] '
  return disabled + (rule.ruleName || rule.id) + '（' + (rule.tableName || '-') + appPoint + '）'
}

function reset() {
  roleId.value = ''
  loadedRoleId.value = null
  assignedRuleIds.value = []
  allRules.value = []
  loading.value = false
  saving.value = false
  loadSeq++
}

defineExpose({ open })
</script>

<style scoped>
.dp-hint {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
  margin-top: 4px;
}
</style>

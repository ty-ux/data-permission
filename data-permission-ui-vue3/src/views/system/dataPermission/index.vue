<template>
  <div class="app-container">
    <el-alert
      class="dp-alert"
      type="info"
      :closable="false"
      show-icon
      title="规则保存后当前实例立即生效；多实例部署请等待 cache-ttl-seconds 缓存过期，或自行广播缓存失效。"
    />

    <el-form ref="queryFormRef" :model="queryParams" size="small" :inline="true" label-width="80px" class="dp-query">
      <el-form-item label="应用点" prop="appPoint">
        <el-input
          v-model="queryParams.appPoint"
          placeholder="如：project"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="queryParams.ruleName"
          placeholder="支持模糊匹配"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleQuery">搜索</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="dp-toolbar">
      <el-button type="primary" plain :icon="Plus" @click="handleAdd">新增规则</el-button>
      <el-button type="warning" plain :icon="User" @click="handleRoleRules">角色分配</el-button>
      <el-button :icon="Refresh" @click="getList">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="pagedRules" border empty-text="暂无规则">
      <el-table-column label="规则名称" prop="ruleName" min-width="150" show-overflow-tooltip />
      <el-table-column label="应用点" prop="appPoint" width="110" align="center">
        <template #default="scope">{{ scope.row.appPoint || '全部' }}</template>
      </el-table-column>
      <el-table-column label="目标表" prop="tableName" min-width="130" show-overflow-tooltip />
      <el-table-column label="类型" width="90" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.ruleMode === 'COMPLEX' ? 'warning' : 'success'">
            {{ scope.row.ruleMode === 'COMPLEX' ? '复杂' : '简单' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="条件" min-width="260" show-overflow-tooltip>
        <template #default="scope">{{ describeRule(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="优先级" prop="priority" width="80" align="center" />
      <el-table-column width="80" align="center">
        <template #header>
          <el-tooltip content="与同表的上一条规则组合，首条规则忽略该值" placement="top">
            <span>组合</span>
          </el-tooltip>
        </template>
        <template #default="scope">
          <el-tag size="small" :type="scope.row.logicOperator === 'OR' ? 'warning' : 'info'">
            {{ scope.row.logicOperator === 'OR' ? 'OR' : 'AND' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="scope">
          <el-tag size="small" :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="description" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="scope">
          <el-button size="small" type="primary" link :icon="Edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="dp-pagination">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="ruleList.length"
        :page-sizes="[10, 20, 50]"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <rule-dialog ref="ruleDialogRef" @saved="getList" />
    <role-rules-dialog ref="roleRulesDialogRef" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, User } from '@element-plus/icons-vue'
import { deleteRules, listRules } from '@/api/system/dataPermission'
import RuleDialog from './components/RuleDialog.vue'
import RoleRulesDialog from './components/RoleRulesDialog.vue'

const OPERATOR_LABELS = {
  '=': '=',
  '!=': '!=',
  '>': '>',
  '>=': '>=',
  '<': '<',
  '<=': '<=',
  LIKE: 'LIKE',
  IN: 'IN',
  IS_NULL: '为空',
  IS_NOT_NULL: '不为空'
}

const loading = ref(false)
const ruleList = ref([])
const queryParams = reactive({
  appPoint: '',
  ruleName: ''
})
const pageNum = ref(1)
const pageSize = ref(10)
const queryFormRef = ref()
const ruleDialogRef = ref()
const roleRulesDialogRef = ref()

let requestSeq = 0

const pagedRules = computed(() => {
  const start = (pageNum.value - 1) * pageSize.value
  return ruleList.value.slice(start, start + pageSize.value)
})

function getList() {
  const seq = ++requestSeq
  loading.value = true
  listRules(queryParams)
    .then(res => {
      if (seq !== requestSeq) {
        return
      }
      ruleList.value = res || []
      const maxPage = Math.max(1, Math.ceil(ruleList.value.length / pageSize.value))
      if (pageNum.value > maxPage) {
        pageNum.value = maxPage
      }
    })
    .catch(() => {})
    .finally(() => {
      if (seq === requestSeq) {
        loading.value = false
      }
    })
}

function handleQuery() {
  pageNum.value = 1
  getList()
}

function resetQuery() {
  queryParams.appPoint = ''
  queryParams.ruleName = ''
  pageNum.value = 1
  getList()
}

function handleAdd() {
  ruleDialogRef.value && ruleDialogRef.value.open()
}

function handleEdit(row) {
  ruleDialogRef.value && ruleDialogRef.value.open(row.id)
}

function handleDelete(row) {
  const name = row.ruleName || row.id
  ElMessageBox.confirm(
    '确认删除规则「' + name + '」？删除后该规则会从所有角色解绑；若角色因此没有可用的启用规则，将看不到受管数据表的数据。',
    '提示',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
    .then(() => deleteRules(row.id))
    .then(() => {
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => {})
}

function handleRoleRules() {
  roleRulesDialogRef.value && roleRulesDialogRef.value.open()
}

function handleSizeChange() {
  pageNum.value = 1
}

function handleCurrentChange() {
  // v-model:current-page 已同步页码，保留事件以兼容 RuoYi 页面习惯
}

function describeRule(row) {
  if (row.ruleMode === 'COMPLEX') {
    const count = (row.items || []).length
    return count ? count + ' 条明细（组间 OR / 子组间 AND）' : '未配置明细'
  }
  const operator = OPERATOR_LABELS[row.conditionType] || row.conditionType || '?'
  if (row.conditionType === 'IS_NULL' || row.conditionType === 'IS_NOT_NULL') {
    return (row.fieldName || '?') + ' ' + operator
  }
  const value =
    row.conditionValue === null || row.conditionValue === undefined || row.conditionValue === ''
      ? '?'
      : row.conditionValue
  return (row.fieldName || '?') + ' ' + operator + ' ' + value
}

onMounted(getList)
</script>

<style scoped>
.dp-alert {
  margin-bottom: 16px;
}

.dp-query {
  margin-bottom: 4px;
}

.dp-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.dp-toolbar .el-button + .el-button {
  margin-left: 0;
}

.dp-pagination {
  margin-top: 16px;
  text-align: right;
}
</style>

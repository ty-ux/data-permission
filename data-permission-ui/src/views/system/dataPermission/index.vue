<template>
  <div class="app-container">
    <el-alert
      class="dp-alert"
      type="info"
      :closable="false"
      show-icon
      title="规则保存后当前实例立即生效；多实例部署请等待 cache-ttl-seconds 缓存过期，或自行广播缓存失效。"
    />

    <el-form ref="queryForm" :model="queryParams" size="small" :inline="true" label-width="80px" class="dp-query">
      <el-form-item label="应用点" prop="appPoint">
        <el-input
          v-model="queryParams.appPoint"
          placeholder="如：project"
          clearable
          style="width: 180px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="queryParams.ruleName"
          placeholder="支持模糊匹配"
          clearable
          style="width: 200px"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="el-icon-plus" size="mini" @click="handleAdd">新增规则</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="el-icon-user" size="mini" @click="handleRoleRules">角色分配</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button icon="el-icon-refresh" size="mini" @click="getList">刷新</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="pagedRules" border empty-text="暂无规则">
      <el-table-column label="规则名称" prop="ruleName" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="应用点" prop="appPoint" width="110" align="center">
        <template slot-scope="scope">{{ scope.row.appPoint || '全部' }}</template>
      </el-table-column>
      <el-table-column label="目标表" prop="tableName" min-width="130" :show-overflow-tooltip="true" />
      <el-table-column label="类型" width="90" align="center">
        <template slot-scope="scope">
          <el-tag size="small" :type="scope.row.ruleMode === 'COMPLEX' ? 'warning' : 'success'">
            {{ scope.row.ruleMode === 'COMPLEX' ? '复杂' : '简单' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="条件" min-width="260" :show-overflow-tooltip="true">
        <template slot-scope="scope">{{ describeRule(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="优先级" prop="priority" width="80" align="center" />
      <el-table-column width="80" align="center">
        <template slot="header">
          <el-tooltip content="与同表的上一条规则组合，首条规则忽略该值" placement="top">
            <span>组合</span>
          </el-tooltip>
        </template>
        <template slot-scope="scope">
          <el-tag size="small" :type="scope.row.logicOperator === 'OR' ? 'warning' : 'info'">
            {{ scope.row.logicOperator === 'OR' ? 'OR' : 'AND' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template slot-scope="scope">
          <el-tag size="small" :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="description" min-width="150" :show-overflow-tooltip="true" />
      <el-table-column label="操作" width="140" align="center" fixed="right" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button size="mini" type="text" icon="el-icon-edit" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button size="mini" type="text" icon="el-icon-delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="dp-pagination">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="ruleList.length"
        :page-sizes="[10, 20, 50]"
        :page-size="pageSize"
        :current-page="pageNum"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <rule-dialog ref="ruleDialog" @saved="getList" />
    <role-rules-dialog ref="roleRulesDialog" />
  </div>
</template>

<script>
import { listRules, deleteRules } from '@/api/system/dataPermission'
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

export default {
  name: 'DataPermission',
  components: { RuleDialog, RoleRulesDialog },
  data() {
    return {
      loading: false,
      ruleList: [],
      queryParams: {
        appPoint: '',
        ruleName: ''
      },
      pageNum: 1,
      pageSize: 10,
      requestSeq: 0
    }
  },
  computed: {
    pagedRules() {
      const start = (this.pageNum - 1) * this.pageSize
      return this.ruleList.slice(start, start + this.pageSize)
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      const seq = ++this.requestSeq
      this.loading = true
      listRules(this.queryParams)
        .then(res => {
          if (seq !== this.requestSeq) {
            return
          }
          this.ruleList = res || []
          const maxPage = Math.max(1, Math.ceil(this.ruleList.length / this.pageSize))
          if (this.pageNum > maxPage) {
            this.pageNum = maxPage
          }
        })
        .catch(() => {})
        .finally(() => {
          if (seq === this.requestSeq) {
            this.loading = false
          }
        })
    },
    handleQuery() {
      this.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.queryParams = { appPoint: '', ruleName: '' }
      this.pageNum = 1
      this.getList()
    },
    handleAdd() {
      this.$refs.ruleDialog.open()
    },
    handleEdit(row) {
      this.$refs.ruleDialog.open(row.id)
    },
    handleDelete(row) {
      const name = row.ruleName || row.id
      this.$confirm('确认删除规则「' + name + '」？删除后该规则会从所有角色解绑；若角色因此没有可用的启用规则，将看不到受管数据表的数据。', '提示', {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
        .then(() => deleteRules(row.id))
        .then(() => {
          this.$message.success('删除成功')
          this.getList()
        })
        .catch(() => {})
    },
    handleRoleRules() {
      this.$refs.roleRulesDialog.open()
    },
    handleSizeChange(size) {
      this.pageSize = size
      this.pageNum = 1
    },
    handleCurrentChange(page) {
      this.pageNum = page
    },
    describeRule(row) {
      if (row.ruleMode === 'COMPLEX') {
        const count = (row.items || []).length
        return count ? count + ' 条明细（组间 OR / 子组间 AND）' : '未配置明细'
      }
      const operator = OPERATOR_LABELS[row.conditionType] || row.conditionType || '?'
      if (row.conditionType === 'IS_NULL' || row.conditionType === 'IS_NOT_NULL') {
        return (row.fieldName || '?') + ' ' + operator
      }
      const value = row.conditionValue === null || row.conditionValue === undefined || row.conditionValue === ''
        ? '?'
        : row.conditionValue
      return (row.fieldName || '?') + ' ' + operator + ' ' + value
    }
  }
}
</script>

<style scoped>
.dp-alert {
  margin-bottom: 16px;
}

.dp-query {
  margin-bottom: 4px;
}

.mb8 {
  margin-bottom: 8px;
}

.dp-pagination {
  margin-top: 16px;
  text-align: right;
}
</style>

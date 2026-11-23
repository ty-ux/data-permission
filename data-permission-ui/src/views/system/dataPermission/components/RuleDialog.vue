<template>
  <el-dialog
    :title="form.id ? '编辑规则' : '新增规则'"
    :visible.sync="visible"
    width="960px"
    append-to-body
    :close-on-click-modal="false"
    :show-close="!submitting"
    :before-close="handleClose"
    @closed="reset"
  >
    <div v-loading="loading">
      <p v-if="validationError" ref="validationSummary" role="alert" tabindex="-1">{{ validationError }}</p>
      <el-form ref="ruleForm" :model="form" :rules="rules" label-width="96px" size="small" :disabled="submitting">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="如：本部门项目" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用点" prop="appPoint">
              <el-input v-model="form.appPoint" placeholder="留空表示按表名匹配所有应用点" maxlength="64" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标表名" prop="tableName">
              <el-input v-model="form.tableName" placeholder="如：project" maxlength="128" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规则类型" prop="ruleMode">
              <el-radio-group v-model="form.ruleMode">
                <el-radio-button label="SIMPLE">简单规则</el-radio-button>
                <el-radio-button label="COMPLEX">复杂规则</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="优先级" prop="priority">
              <el-input-number v-model="form.priority" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="组合方式" prop="logicOperator">
              <el-select v-model="form.logicOperator" style="width: 100%">
                <el-option label="AND（与上一条）" value="AND" />
                <el-option label="OR（或上一条）" value="OR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="启用" prop="enabled">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="form.ruleMode === 'SIMPLE'">
          <el-divider content-position="left">简单规则</el-divider>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="过滤字段" prop="fieldName">
                <el-input v-model="form.fieldName" placeholder="如：dept_id" maxlength="128" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="操作符" prop="conditionType">
                <el-select v-model="form.conditionType" style="width: 100%">
                  <el-option v-for="op in operatorOptions" :key="op.value" :label="op.label" :value="op.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="值" prop="conditionValue">
                <el-input v-model="form.conditionValue" placeholder="100、#{deptId} 或 A,B" maxlength="500" />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="dp-hint">
            值支持常量、<code>#{deptId}</code>（当前用户字段）和裸字段名；<code>IN</code> 用逗号分隔或传集合；<code>LIKE</code> 会自动加首尾 %。
          </div>
        </template>

        <template v-else>
          <el-divider content-position="left">复杂规则</el-divider>
          <el-table :data="form.items" border size="small" class="dp-item-table">
            <el-table-column label="组" width="60">
              <template slot-scope="scope">
                <el-input-number v-model="scope.row.groupNo" :min="1" :max="99" :controls="false" size="mini" />
              </template>
            </el-table-column>
            <el-table-column label="子组" width="60">
              <template slot-scope="scope">
                <el-input-number v-model="scope.row.subGroupNo" :min="1" :max="99" :controls="false" size="mini" />
              </template>
            </el-table-column>
            <el-table-column label="排序" width="60">
              <template slot-scope="scope">
                <el-input-number v-model="scope.row.sortOrder" :min="0" :max="9999" :controls="false" size="mini" />
              </template>
            </el-table-column>
            <el-table-column label="与上一条" width="90">
              <template slot-scope="scope">
                <el-select v-model="scope.row.logicOperator" size="mini" style="width: 100%">
                  <el-option label="AND" value="AND" />
                  <el-option label="OR" value="OR" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="左字段" min-width="125">
              <template slot-scope="scope">
                <el-input v-model="scope.row.leftExpr" size="mini" maxlength="255" placeholder="dept_id" />
              </template>
            </el-table-column>
            <el-table-column label="操作符" width="110">
              <template slot-scope="scope">
                <el-select v-model="scope.row.operator" size="mini" style="width: 100%">
                  <el-option v-for="op in operatorOptions" :key="op.value" :label="op.label" :value="op.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="右值类型" width="125">
              <template slot-scope="scope">
                <el-select v-model="scope.row.rightType" size="mini" style="width: 100%">
                  <el-option v-for="type in rightTypeOptions" :key="type.value" :label="type.label" :value="type.value" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="右值" min-width="125">
              <template slot-scope="scope">
                <template v-if="scope.row.rightType === 'SUBQUERY'">
                  <label :for="'dp-sql-' + scope.$index">子查询 SQL（必填）</label>
                  <div :id="'dp-sql-hint-' + scope.$index">仅单条只读 SELECT，最多 4000 字符；操作符须选 IN。</div>
                  <el-input :id="'dp-sql-' + scope.$index" v-model="scope.row.rightExpr" type="textarea" :rows="5"
                    maxlength="4000" required :spellcheck="false" autocorrect="off" autocapitalize="none"
                    :aria-describedby="'dp-sql-hint-' + scope.$index" resize="vertical" />
                  <el-select size="mini" placeholder="插入用户占位符" :value="null"
                    :aria-label="'第 ' + (scope.$index + 1) + ' 条明细插入占位符'"
                    @change="field => insertPlaceholder(scope.row, field)">
                    <el-option v-for="field in placeholderFields" :key="field" :label="field" :value="field" />
                  </el-select>
                </template>
                <el-input v-else v-model="scope.row.rightExpr" size="mini" maxlength="500" placeholder="deptId 或 100" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" icon="el-icon-delete" size="mini" :disabled="submitting" @click="removeItem(scope.$index)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" plain icon="el-icon-plus" size="mini" class="dp-add-item" :disabled="submitting" @click="addItem">
            添加明细
          </el-button>
          <div class="dp-hint">
            组间 OR、子组间 AND；同子组内按「与上一条」组合。右值类型：常量/动态值填值或 <code>#{字段}</code>；用户字段填当前用户属性名；字段填另一个列名；IS NULL / IS NOT NULL 可忽略右值。
          </div>
        </template>

        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
    </div>
    <span slot="footer" class="dialog-footer">
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { getRule, saveRule, validateRule, subqueryFields } from '@/api/system/dataPermission'

export default {
  name: 'RuleDialog',
  data() {
    return {
      visible: false,
      loading: false,
      submitting: false,
      session: 0,
      placeholderFields: [],
      validationError: '',
      form: this.blankForm(),
      operatorOptions: [
        { label: '=', value: '=' },
        { label: '!=', value: '!=' },
        { label: '>', value: '>' },
        { label: '>=', value: '>=' },
        { label: '<', value: '<' },
        { label: '<=', value: '<=' },
        { label: 'LIKE', value: 'LIKE' },
        { label: 'IN', value: 'IN' },
        { label: 'IS NULL', value: 'IS_NULL' },
        { label: 'IS NOT NULL', value: 'IS_NOT_NULL' }
      ],
      rightTypeOptions: [
        { label: '常量/动态', value: 'CONST' },
        { label: '用户字段', value: 'CURRENT_USER_FIELD' },
        { label: '字段', value: 'FIELD' },
        { label: '子查询 SQL', value: 'SUBQUERY' },
        { label: '无', value: 'NONE' }
      ],
      rules: {
        ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
        tableName: [{ required: true, message: '请输入目标表名', trigger: 'blur' }],
        ruleMode: [{ required: true, message: '请选择规则类型', trigger: 'change' }]
      }
    }
  },
  methods: {
    open(ruleId) {
      const token = ++this.session
      subqueryFields().then(fields => { if (token === this.session) this.placeholderFields = fields }).catch(() => {})
      this.visible = true
      this.form = this.blankForm()
      this.$nextTick(() => {
        if (this.$refs.ruleForm) {
          this.$refs.ruleForm.clearValidate()
        }
      })
      if (!ruleId) {
        this.loading = false
        return
      }
      this.loading = true
      getRule(ruleId)
        .then(res => {
          if (token !== this.session) {
            return
          }
          if (!res) {
            this.$message.error('规则不存在或已被删除')
            this.visible = false
            return
          }
          this.form = this.normalize(res)
        })
        .catch(() => {
          if (token === this.session) {
            this.visible = false
          }
        })
        .finally(() => {
          if (token === this.session) {
            this.loading = false
          }
        })
    },
    handleClose(done) {
      if (this.submitting) {
        return
      }
      done()
    },
    blankForm() {
      return {
        id: null,
        ruleName: '',
        appPoint: '',
        tableName: '',
        fieldName: '',
        conditionType: '=',
        conditionValue: '#{deptId}',
        ruleMode: 'SIMPLE',
        logicOperator: 'AND',
        sortOrder: 0,
        priority: 0,
        enabled: true,
        description: '',
        items: []
      }
    },
    blankItem(index) {
      return {
        groupNo: 1,
        subGroupNo: 1,
        sortOrder: index || 0,
        logicOperator: 'AND',
        leftType: 'FIELD',
        leftExpr: '',
        operator: '=',
        rightType: 'CURRENT_USER_FIELD',
        rightExpr: 'deptId'
      }
    },
    normalize(rule) {
      const form = Object.assign(this.blankForm(), rule)
      form.enabled = rule.enabled !== false && rule.enabled !== '0'
      form.ruleMode = rule.ruleMode === 'COMPLEX' ? 'COMPLEX' : 'SIMPLE'
      form.logicOperator = rule.logicOperator === 'OR' ? 'OR' : 'AND'
      form.priority = Number(rule.priority || 0)
      form.sortOrder = Number(rule.sortOrder || 0)
      form.items = (rule.items || []).map((item, index) => {
        const noRightValue = item.operator === 'IS_NULL' || item.operator === 'IS_NOT_NULL'
        return Object.assign(this.blankItem(index), item, {
          groupNo: Number(item.groupNo || 1),
          subGroupNo: Number(item.subGroupNo || 1),
          sortOrder: Number(item.sortOrder === null || item.sortOrder === undefined ? index : item.sortOrder),
          logicOperator: item.logicOperator === 'OR' ? 'OR' : 'AND',
          leftType: item.leftType || 'FIELD',
          rightType: noRightValue ? 'NONE' : (item.rightType || 'CONST')
        })
      })
      return form
    },
    insertPlaceholder(item, field) {
      item.rightExpr = (item.rightExpr || '') + '#{' + field + '}'
    },
    addItem() {
      this.form.items.push(this.blankItem(this.form.items.length))
    },
    removeItem(index) {
      this.form.items.splice(index, 1)
    },
    trimForm() {
      const form = this.form
      form.ruleName = (form.ruleName || '').trim()
      form.appPoint = (form.appPoint || '').trim()
      form.tableName = (form.tableName || '').trim()
      form.fieldName = (form.fieldName || '').trim()
      form.description = (form.description || '').trim()
      form.items.forEach(item => {
        item.leftExpr = (item.leftExpr || '').trim()
        item.rightExpr = (item.rightExpr || '').trim()
      })
    },
    reset() {
      this.form = this.blankForm()
      this.validationError = ''
      this.loading = false
      this.submitting = false
    },
    submit() {
      if (this.submitting) {
        return
      }
      const token = this.session
      this.validationError = ''
      this.trimForm()
      this.$refs.ruleForm.validate(valid => {
        if (!valid) {
          return
        }
        if (this.form.ruleMode === 'SIMPLE') {
          if (!this.form.fieldName || !this.form.conditionType) {
            this.$message.error('简单规则需要填写过滤字段和操作符')
            return
          }
          const noRightValue = this.form.conditionType === 'IS_NULL' || this.form.conditionType === 'IS_NOT_NULL'
          if (!noRightValue && !String(this.form.conditionValue || '').trim()) {
            this.$message.error('简单规则需要填写条件值')
            return
          }
        } else {
          if (!this.form.items.length) {
            this.$message.error('复杂规则至少需要一条明细')
            return
          }
          const badIndex = this.form.items.findIndex(item => !item.leftExpr || !item.operator)
          if (badIndex >= 0) {
            this.$message.error('第 ' + (badIndex + 1) + ' 条明细缺少左字段或操作符')
            return
          }
          const noRightValue = item => item.operator === 'IS_NULL' || item.operator === 'IS_NOT_NULL'
          const noneIndex = this.form.items.findIndex(item => !noRightValue(item) && (!item.rightType || item.rightType === 'NONE'))
          if (noneIndex >= 0) {
            this.$message.error('第 ' + (noneIndex + 1) + ' 条明细的右值类型不能为“无”')
            return
          }
          const emptyRightIndex = this.form.items.findIndex(item => !noRightValue(item) && !item.rightExpr)
          if (emptyRightIndex >= 0) {
            this.$message.error('第 ' + (emptyRightIndex + 1) + ' 条明细缺少右值')
            return
          }
          this.form.items.forEach(item => {
            if (noRightValue(item)) {
              item.rightType = 'NONE'
              item.rightExpr = ''
            }
          })
        }
        this.submitting = true
        const payload = JSON.parse(JSON.stringify(this.form))
        if (payload.ruleMode === 'SIMPLE') {
          payload.items = []
        } else {
          payload.items.forEach(item => {
            item.leftType = item.leftType || 'FIELD'
          })
        }
        validateRule(payload).then(() => saveRule(payload))
          .then(() => {
            if (token !== this.session) {
              return
            }
            this.$message.success('保存成功')
            this.visible = false
            this.$emit('saved')
          })
          .catch(error => {
            if (token !== this.session) return
            this.validationError = (error.response && error.response.data && error.response.data.error) || error.message || '校验或保存失败，请检查规则后重试'
            this.$nextTick(() => { if (this.$refs.validationSummary) this.$refs.validationSummary.focus() })
          })
          .finally(() => {
            if (token === this.session) {
              this.submitting = false
            }
          })
      })
    }
  }
}
</script>

<style scoped>
.dp-hint {
  color: #909399;
  font-size: 12px;
  line-height: 20px;
  margin: 4px 0 12px;
}

.dp-item-table {
  width: 100%;
}

.dp-add-item {
  margin-top: 8px;
}

.dp-item-table ::v-deep .el-input-number {
  width: 100%;
}
</style>

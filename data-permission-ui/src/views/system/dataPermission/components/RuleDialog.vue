<template>
  <el-dialog
    :title="form.id ? '编辑可见范围' : '新增可见范围'"
    :visible.sync="visible" width="960px" custom-class="dp-rule-dialog" top="5vh"
    append-to-body :close-on-click-modal="false" :show-close="!submitting"
    :before-close="handleClose" @closed="reset"
  >
    <div v-loading="loading">
      <p v-if="validationError" ref="validationSummary" role="alert" tabindex="-1" class="dp-error">{{ validationError }}</p>
      <el-form ref="ruleForm" :model="form" :rules="rules" label-position="top" size="small" :disabled="submitting || loading" @submit.native.prevent="submit">
        <div class="dp-basics">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="form.ruleName" placeholder="例如：我负责或参与的工单" maxlength="128" />
          </el-form-item>
          <el-form-item label="规则状态" prop="enabled">
            <el-switch v-model="form.enabled" active-text="已启用" inactive-text="已停用" />
          </el-form-item>
        </div>
        <section class="dp-preview" aria-label="规则内容预览">
          <strong>这条规则允许查看</strong>
          <p>{{ rulePreview }}</p>
          <span>保存并分配给角色后参与生效；最终范围还受该角色的其他规则影响。</span>
        </section>

        <div class="dp-section-heading">
          <h3>可见范围</h3>
          <el-radio-group v-model="form.ruleMode" size="small">
            <el-radio-button label="SIMPLE">单个条件</el-radio-button>
            <el-radio-button label="COMPLEX">多个条件</el-radio-button>
          </el-radio-group>
        </div>
        <template v-if="form.ruleMode === 'SIMPLE'">
          <div class="dp-single">
            <div class="dp-simple-fields">
              <label>数据字段
                <el-select v-model="form.fieldName" filterable allow-create default-first-option placeholder="选择或输入字段">
                  <el-option v-if="form.fieldName && !fieldLabels[form.fieldName]" :value="form.fieldName" :label="fieldLabel(form.fieldName)" />
                  <el-option v-for="(label, value) in fieldLabels" :key="value" :label="label" :value="value" />
                </el-select>
              </label>
              <label>判断关系
                <el-select v-model="form.conditionType">
                  <el-option v-for="op in operatorOptions" :key="op.value" :label="op.label" :value="op.value" />
                </el-select>
              </label>
              <label v-if="!['IS_NULL', 'IS_NOT_NULL'].includes(form.conditionType)">对比内容
                <el-select v-model="form.conditionValue" filterable allow-create default-first-option placeholder="选择当前用户信息或输入指定值">
                  <el-option v-for="(label, value) in userLabels" :key="value" :label="label" :value="'#{' + value + '}'" />
                </el-select>
              </label>
            </div>
            <p class="dp-hint">选择当前用户信息会随登录人变化；也可输入指定值，多个值用逗号分隔。</p>
          </div>
        </template>
        <template v-else>
          <p class="dp-hint">满足下面任一范围，即符合这条规则。</p>
          <div v-if="form.tableName === 'ppm_task'" class="dp-presets">
            <span>快捷添加</span>
            <el-button v-for="scope in taskScopes" :key="scope.label" plain size="small" @click="addTaskScope(scope)">{{ scope.label }}</el-button>
          </div>
          <div v-if="!conditionGroups.length" class="dp-empty">还没有条件，添加一个可见范围开始配置。</div>
          <template v-for="(group, groupIndex) in conditionGroups">
            <div v-if="groupIndex" :key="'or-' + group.no" class="dp-or">或者</div>
            <section :key="'group-' + group.no" class="dp-range">
              <div class="dp-range-heading"><h4>范围 {{ groupIndex + 1 }}</h4><span>{{ group.subgroups.length > 1 ? '以下条件块需要同时满足' : '满足以下条件' }}</span></div>
              <div v-for="(subgroup, subIndex) in group.subgroups" :key="subgroup.no" class="dp-subgroup">
                <div v-if="subIndex" class="dp-and">并且满足</div>
                <div v-for="(item, itemIndex) in subgroup.items" :key="form.items.indexOf(item)" class="dp-condition">
                  <div class="dp-condition-heading">
                    <strong v-if="!itemIndex">{{ taskScopeLabel(item, form.tableName) || (subIndex ? '条件块 ' + (subIndex + 1) : '满足条件') }}</strong>
                    <label v-else class="dp-join">与前面条件的结果
                      <el-select v-model="item.logicOperator" size="small">
                        <el-option label="同时满足（且）" value="AND" /><el-option label="满足任一（或）" value="OR" />
                      </el-select>
                    </label>
                    <el-button type="text" icon="el-icon-delete" :aria-label="'删除范围 ' + (groupIndex + 1) + ' 条件块 ' + (subIndex + 1) + ' 的第 ' + (itemIndex + 1) + ' 个条件'" @click="removeItem(form.items.indexOf(item))">删除条件</el-button>
                  </div>
                  <condition-editor :item="item" :table-name="form.tableName" :placeholder-fields="placeholderFields" />
                </div>
                <el-button v-if="subgroup.items.length > 1 || !taskScopeLabel(subgroup.items[0], form.tableName)" plain size="small" icon="el-icon-plus" @click="addCondition(group.no, subgroup.no)">添加条件</el-button>
              </div>
              <details class="dp-more-conditions">
                <summary>增加限制条件</summary>
                <el-button type="text" @click="addConditionBlock(group.no)">再同时满足一个条件块</el-button>
              </details>
            </section>
          </template>
          <el-button plain icon="el-icon-plus" class="dp-add-item" @click="addRange">添加另一种可见范围</el-button>
        </template>

        <details ref="advancedSettings" class="dp-advanced">
          <summary>高级配置 <span>适用位置、执行顺序和技术参数</span></summary>
          <p class="dp-hint">首次接入由技术人员确认。已有规则可以直接保留原配置。</p>
          <div class="dp-settings-grid">
            <el-form-item label="数据表名（必填）" prop="tableName"><el-input v-model="form.tableName" placeholder="例如：ppm_task" maxlength="128" /></el-form-item>
            <el-form-item label="应用位置标识" prop="appPoint"><el-input v-model="form.appPoint" placeholder="留空表示所有应用位置" maxlength="64" /></el-form-item>
            <el-form-item label="执行优先级（越大越先）"><el-input-number v-model="form.priority" :min="0" :max="9999" controls-position="right" /></el-form-item>
            <el-form-item label="同优先级排序（越小越先）"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" /></el-form-item>
            <el-form-item label="与前面规则的关系">
              <el-select v-model="form.logicOperator"><el-option label="同时满足（交集）" value="AND" /><el-option label="满足任一（并集）" value="OR" /></el-select>
            </el-form-item>
          </div>
          <p class="dp-hint">第一条规则忽略组合关系。以下原始参数与上方范围同步，调整后请核对文字预览。</p>
          <div v-for="(item, index) in form.ruleMode === 'COMPLEX' ? form.items : []" :key="index" class="dp-raw-item">
            <strong>条件 {{ index + 1 }} · {{ fieldLabel(item.leftExpr) }}</strong>
            <div class="dp-raw-grid">
              <label>范围编号<el-input-number v-model="item.groupNo" :min="1" :max="99" controls-position="right" /></label>
              <label>条件块编号<el-input-number v-model="item.subGroupNo" :min="1" :max="99" controls-position="right" /></label>
              <label>执行排序<el-input-number v-model="item.sortOrder" :min="0" :max="9999" controls-position="right" /></label>
              <label>实际字段<el-input v-model="item.leftExpr" maxlength="255" /></label>
            </div>
          </div>
          <el-form-item v-if="form.ruleMode === 'SIMPLE'" label="实际过滤字段"><el-input v-model="form.fieldName" maxlength="128" /></el-form-item>
        </details>
        <el-form-item label="备注（选填）" prop="description" class="dp-description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="说明这条规则用于哪些人或场景" />
        </el-form-item>
      </el-form>
    </div>
    <span slot="footer" class="dialog-footer">
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="loading" :loading="submitting" @click="submit">保存规则</el-button>
    </span>
  </el-dialog>
</template>

<script>
import { getRule, saveRule, validateRule, subqueryFields } from '@/api/system/dataPermission'
import ConditionEditor from './ConditionEditor.vue'
import { fieldLabels, fieldLabel, userLabels, operatorLabels, groupItems, describeRule, taskScopes, taskScopeLabel } from '../rulePresentation'

export default {
  name: 'RuleDialog',
  components: { ConditionEditor },
  data() {
    return {
      visible: false,
      loading: false,
      submitting: false,
      session: 0,
      placeholderFields: [],
      validationError: '',
      form: this.blankForm(),
      fieldLabels, userLabels, taskScopes,
      operatorOptions: Object.keys(operatorLabels).map(value => ({ value, label: operatorLabels[value] })),
      rules: {
        ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
        tableName: [{ required: true, message: '请输入目标表名', trigger: 'blur' }],
        ruleMode: [{ required: true, message: '请选择规则类型', trigger: 'change' }]
      }
    }
  },
  computed: {
    conditionGroups() { return groupItems(this.form.items) },
    rulePreview() { return describeRule(this.form) }
  },
  methods: {
    fieldLabel,
    taskScopeLabel,
    addTaskScope(scope) {
      const previousLength = this.form.items.length
      this.addRange()
      if (this.form.items.length > previousLength) {
        const { label, ...condition } = scope
        Object.assign(this.form.items[this.form.items.length - 1], condition)
      }
    },
    addCondition(groupNo, subGroupNo) {
      const sortOrder = Math.max(-1, ...this.form.items.map(item => item.sortOrder)) + 1
      this.form.items.push(Object.assign(this.blankItem(sortOrder), { groupNo, subGroupNo }))
    },
    addConditionBlock(groupNo) {
      const subGroupNo = Math.max(0, ...this.form.items.filter(item => item.groupNo === groupNo).map(item => item.subGroupNo)) + 1
      if (subGroupNo > 99) { this.$message.warning('每个范围最多支持 99 个条件块'); return }
      this.addCondition(groupNo, subGroupNo)
    },
    addRange() {
      const groupNo = Math.max(0, ...this.form.items.map(item => item.groupNo)) + 1
      if (groupNo > 99) { this.$message.warning('最多支持 99 个可见范围，请先整理现有范围'); return }
      this.addCondition(groupNo, 1)
    },
    open(ruleId) {
      this.validationError = ''
      const token = ++this.session
      subqueryFields().then(fields => { if (token === this.session) this.placeholderFields = fields }).catch(() => {})
      this.visible = true
      this.form = this.blankForm()
      this.$nextTick(() => {
        if (this.$refs.advancedSettings) this.$refs.advancedSettings.open = !ruleId
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
      if (this.submitting || this.loading) {
        return
      }
      const token = this.session
      this.validationError = ''
      this.trimForm()
      if (!this.form.tableName && this.$refs.advancedSettings) this.$refs.advancedSettings.open = true
      this.$refs.ruleForm.validate(valid => {
        if (!valid) {
          return
        }
        if (this.form.ruleMode === 'SIMPLE') {
          if (!this.form.fieldName || !this.form.conditionType) {
            this.$message.error('请选择数据字段和判断关系')
            return
          }
          const noRightValue = this.form.conditionType === 'IS_NULL' || this.form.conditionType === 'IS_NOT_NULL'
          if (!noRightValue && !String(this.form.conditionValue || '').trim()) {
            this.$message.error('请填写对比内容')
            return
          }
        } else {
          if (!this.form.items.length) {
            this.$message.error('请至少添加一个可见范围')
            return
          }
          const badIndex = this.form.items.findIndex(item => !item.leftExpr || !item.operator)
          if (badIndex >= 0) {
            this.$message.error('第 ' + (badIndex + 1) + ' 个条件缺少数据字段或判断关系')
            return
          }
          const noRightValue = item => item.operator === 'IS_NULL' || item.operator === 'IS_NOT_NULL'
          const noneIndex = this.form.items.findIndex(item => !noRightValue(item) && (!item.rightType || item.rightType === 'NONE'))
          if (noneIndex >= 0) {
            this.$message.error('第 ' + (noneIndex + 1) + ' 个条件请选择有效的取值来源')
            return
          }
          const emptyRightIndex = this.form.items.findIndex(item => !noRightValue(item) && !item.rightExpr)
          if (emptyRightIndex >= 0) {
            this.$message.error('第 ' + (emptyRightIndex + 1) + ' 个条件缺少对比内容')
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
.dp-basics { display: grid; grid-template-columns: minmax(0, 1fr) 200px; gap: 24px; }
.dp-preview { padding: 16px 20px; background: #f0f7ff; border-left: 3px solid #409eff; margin-bottom: 24px; line-height: 1.8; overflow-wrap: anywhere; }
.dp-preview strong { color: #245888; }
.dp-preview p { color: #263c50; margin: 8px 0; font-size: 15px; }
.dp-preview span, .dp-hint { font-size: 12px; color: #606b7a; line-height: 1.8; }
.dp-section-heading, .dp-range-heading, .dp-condition-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
h3 { font-size: 16px; margin: 0; color: #303133; }
h4 { font-size: 14px; margin: 0; color: #303133; }
.dp-range { border: 1px solid #dce3eb; border-radius: 6px; padding: 16px 20px; }
.dp-range-heading { padding-bottom: 12px; border-bottom: 1px solid #ebeef5; }
.dp-range-heading span { font-size: 12px; color: #606b7a; }
.dp-condition { padding: 8px 0 14px; }
.dp-condition-heading { margin-bottom: 8px; font-size: 13px; }
.dp-condition-heading strong { font-weight: 500; }
.dp-join { display: flex; align-items: center; gap: 12px; }
.dp-join .el-select { width: 160px; }
.dp-or { text-align: center; padding: 8px; color: #2465a3; font-weight: 600; }
.dp-and { margin: 12px 0 4px; color: #606b7a; font-size: 13px; }
.dp-add-item { margin-top: 16px; }
.dp-presets { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 16px; font-size: 12px; color: #606b7a; }
.dp-presets .el-button { margin: 0; }
.dp-more-conditions { font-size: 12px; color: #606b7a; margin-top: 4px; }
.dp-more-conditions summary { cursor: pointer; }
.dp-advanced { margin: 24px 0 20px; border-top: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; padding: 14px 0; }
.dp-advanced summary { cursor: pointer; font-weight: 500; color: #303133; }
.dp-advanced summary span { font-size: 12px; color: #606b7a; margin-left: 12px; font-weight: 400; }
.dp-settings-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.dp-raw-item { margin: 12px 0; padding: 12px; background: #f7f9fb; }
.dp-raw-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-top: 12px; }
.dp-raw-grid label, .dp-simple-fields label { display: block; color: #4b5563; font-size: 13px; line-height: 1.7; }
.dp-raw-grid .el-input-number { width: 100%; }
.dp-simple-fields { display: grid; grid-template-columns: 1fr .85fr 1.5fr; gap: 12px; }
.dp-simple-fields .el-select { display: block; width: 100%; margin-top: 5px; }
.dp-single { margin-top: 16px; }
.dp-description { margin-bottom: 0; }
.dp-error { color: #b42318; background: #fff2f0; padding: 12px; }
.dp-empty { text-align: center; background: #f7f9fb; padding: 24px; color: #606b7a; }
summary:focus-visible { outline: 2px solid #2465a3; outline-offset: 3px; }
@media (max-width: 640px) {
  .dp-basics, .dp-settings-grid, .dp-raw-grid, .dp-simple-fields { grid-template-columns: 1fr; }
  .dp-section-heading, .dp-range-heading, .dp-condition-heading { flex-wrap: wrap; }
  .dp-range { padding: 12px; }
  .dp-join { flex-wrap: wrap; }
}
</style>
<style>
.dp-rule-dialog { max-width: calc(100vw - 24px); }
.dp-rule-dialog .el-dialog__body { padding: 12px 24px 24px; max-height: 72vh; overflow-y: auto; }
.dp-rule-dialog .el-dialog__footer { border-top: 1px solid #ebeef5; padding: 16px 24px; }
.dp-rule-dialog .el-form--label-top .el-form-item__label { padding-bottom: 5px; line-height: 24px; }
</style>

<template>
  <details class="condition-editor" :open="initiallyExpanded">
    <summary>查看或调整条件</summary>
    <div class="condition-content">
    <div class="condition-fields">
      <label>数据字段
        <el-select v-model="item.leftExpr" filterable allow-create default-first-option placeholder="选择或输入字段">
          <el-option v-if="item.leftExpr && !fieldLabels[item.leftExpr]" :value="item.leftExpr" :label="fieldLabel(item.leftExpr)" />
          <el-option v-for="(label, value) in fieldLabels" :key="value" :value="value" :label="label" />
        </el-select>
      </label>
      <label>判断关系
        <el-select v-model="item.operator">
          <el-option v-for="(label, value) in operatorLabels" :key="value" :value="value" :label="label" />
        </el-select>
      </label>
      <label v-if="!noValue">对比内容
        <el-select v-if="item.rightType === 'CURRENT_USER_FIELD'" v-model="item.rightExpr" filterable allow-create default-first-option>
          <el-option v-for="field in userFields" :key="field" :value="field" :label="userLabels[field] || field" />
        </el-select>
        <span v-else-if="item.rightType === 'SUBQUERY'" class="query-value">关联查询的结果 <span class="muted">在下方展开配置</span></span>
        <el-input v-else v-model="item.rightExpr" maxlength="500" :placeholder="item.rightType === 'FIELD' ? '输入另一个数据字段' : '输入指定值；多个值用逗号分隔'" />
      </label>
    </div>
    <details class="condition-details">
      <summary>配置取值来源<span v-if="item.rightType === 'SUBQUERY'">与关联查询</span></summary>
      <label class="source-label">取值来源
        <el-select v-model="item.rightType" :disabled="noValue">
          <el-option label="当前用户的信息" value="CURRENT_USER_FIELD" />
          <el-option label="指定值或动态参数" value="CONST" />
          <el-option label="另一个数据字段" value="FIELD" />
          <el-option label="关联查询（技术配置）" value="SUBQUERY" />
          <el-option v-if="noValue" label="无需对比内容" value="NONE" />
        </el-select>
      </label>
      <template v-if="!noValue && item.rightType === 'SUBQUERY'">
        <label>关联查询 SQL（必填）
          <el-input v-model="item.rightExpr" type="textarea" :rows="5" maxlength="4000" resize="vertical" :spellcheck="false" autocorrect="off" autocapitalize="none" />
        </label>
        <p class="muted">仅支持单条只读 SELECT，判断关系须选“属于以下范围”，最多 4000 字符。</p>
        <el-select placeholder="插入当前用户信息" :value="null" @change="insertPlaceholder">
          <el-option v-for="field in placeholderFields" :key="field" :label="userLabels[field] || field" :value="field" />
        </el-select>
      </template>
      <p v-else-if="item.rightType === 'CONST'" class="muted">支持固定值、逗号分隔的列表，或动态参数（例如 #{deptId}）。</p>
      <p class="muted">实际字段：{{ item.leftExpr || '尚未选择' }}。字段中文名称仅用于显示，请确认与业务含义一致。</p>
    </details>
    </div>
  </details>
</template>

<script>
import { fieldLabels, fieldLabel, userLabels, operatorLabels, taskScopeLabel } from '../rulePresentation'
export default {
  name: 'ConditionEditor',
  props: {
    item: { type: Object, required: true },
    tableName: { type: String, default: '' },
    placeholderFields: { type: Array, default: () => [] }
  },
  data() {
    return { fieldLabels, userLabels, operatorLabels, initiallyExpanded: !taskScopeLabel(this.item, this.tableName) }
  },
  watch: {
    // Reset disclosure only when a card is reused for another item, never while typing SQL.
    item(value, previous) {
      if (value !== previous) this.initiallyExpanded = !taskScopeLabel(value, this.tableName)
    }
  },
  computed: {
    noValue() { return ['IS_NULL', 'IS_NOT_NULL'].includes(this.item.operator) },
    userFields() { return Array.from(new Set([...Object.keys(userLabels), ...this.placeholderFields, this.item.rightExpr].filter(Boolean))) }
  },
  methods: {
    fieldLabel,
    insertPlaceholder(field) { this.item.rightExpr = (this.item.rightExpr || '') + '#{' + field + '}' }
  }
}
</script>

<style scoped>
.condition-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, .85fr) minmax(0, 1.5fr); gap: 12px; }
.condition-content { padding-top: 12px; }
label { display: block; color: #4b5563; font-size: 13px; line-height: 1.7; }
label > .el-select, label > .el-input, label > .el-textarea { display: block; width: 100%; margin-top: 5px; }
.condition-details { margin-top: 12px; font-size: 13px; }
summary { cursor: pointer; color: #2465a3; padding: 5px 0; width: fit-content; }
.source-label { max-width: 320px; margin: 10px 0; }
.query-value { display: block; padding: 9px 0 0; color: #303133; }
.muted { color: #606b7a; font-size: 12px; line-height: 1.7; }
.query-value .muted { display: block; }
summary:focus-visible { outline: 2px solid #2465a3; outline-offset: 3px; }
@media (max-width: 640px) { .condition-fields { grid-template-columns: 1fr; } }
</style>

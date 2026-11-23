package com.ty.dp.core.sql;

import com.ty.dp.core.exception.DataPermissionException;
import com.ty.dp.core.model.ConditionOperator;
import com.ty.dp.core.model.DataPermissionRule;
import com.ty.dp.core.model.DataPermissionRuleItem;
import com.ty.dp.core.model.LogicOperator;
import com.ty.dp.core.model.RightValueType;
import com.ty.dp.core.model.RuleMode;
import com.ty.dp.core.subject.DataPermissionSubject;
import com.ty.dp.core.util.Reflections;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SubSelect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites a SELECT statement by appending permission predicates.
 *
 * <p>Safety rules:</p>
 * <ul>
 *   <li>An unresolved dynamic value becomes {@code 1 = 0}, never "drop the
 *       condition". Dropping an AND condition widens access.</li>
 *   <li>Parse failures throw {@link DataPermissionException}; the interceptor
 *       decides between logging and rejecting the query.</li>
 *   <li>Column and table identifiers are validated before they are turned into
 *       SQL fragments.</li>
 *   <li>Values are emitted as literals with quotes and backslashes escaped.</li>
 * </ul>
 */
public final class SqlConditionBuilder {

    private static final Logger log = LoggerFactory.getLogger(SqlConditionBuilder.class);

    private static final Pattern DYNAMIC_VALUE =
            Pattern.compile("^#\\{\\s*([A-Za-z_][A-Za-z0-9_.]*)\\s*}$");
    private static final Pattern IDENTIFIER =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_$]*$");
    private static final Object UNRESOLVED = new Object();

    private static final ThreadLocal<Map<String, Object>> BINDINGS = new ThreadLocal<Map<String, Object>>();

    /** Scoped collector: always cleared, including validation failures. */
    public static String applyPrepared(String sql, List<DataPermissionRule> rules, Map<String, String> aliases,
            DataPermissionSubject subject, Object parameter, boolean deny, Map<String, Object> bindings) {
        BINDINGS.set(bindings);
        try { return apply(sql, rules, aliases, subject, parameter, deny); }
        finally { BINDINGS.remove(); }
    }

    private SqlConditionBuilder() {
    }

    public static String apply(String sql,
                               List<DataPermissionRule> rules,
                               Map<String, String> annotationAliases,
                               DataPermissionSubject subject,
                               Object parameter,
                               boolean denyWhenEmpty) {
        if (sql == null || sql.trim().isEmpty() || sql.contains(SqlMarkers.MARKER)) {
            return sql;
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException ex) {
            throw new DataPermissionException("Cannot parse SQL for data permission: " + ex.getMessage(), ex);
        }
        if (!(statement instanceof Select)) {
            return sql;
        }
        boolean changed = addToSelect((Select) statement,
                rules == null ? Collections.<DataPermissionRule>emptyList() : rules,
                annotationAliases == null ? Collections.<String, String>emptyMap() : annotationAliases,
                subject, parameter, denyWhenEmpty);
        if (!changed) {
            if (denyWhenEmpty) {
                throw new DataPermissionException("Cannot apply data permission to SQL: " + sql);
            }
            return sql;
        }
        return SqlMarkers.MARKER + " " + statement.toString();
    }

    public static String denyAll(String sql, Map<String, String> annotationAliases) {
        return apply(sql, Collections.<DataPermissionRule>emptyList(), annotationAliases,
                null, null, true);
    }

    private static boolean addToSelect(Select select,
                                       List<DataPermissionRule> rules,
                                       Map<String, String> annotationAliases,
                                       DataPermissionSubject subject,
                                       Object parameter,
                                       boolean denyWhenEmpty) {
        SelectBody body = select.getSelectBody();
        return addToBody(body, rules, annotationAliases, subject, parameter, denyWhenEmpty);
    }

    private static boolean addToBody(SelectBody body,
                                     List<DataPermissionRule> rules,
                                     Map<String, String> annotationAliases,
                                     DataPermissionSubject subject,
                                     Object parameter,
                                     boolean denyWhenEmpty) {
        if (body instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) body;
            if (plainSelect.getFromItem() instanceof SubSelect) {
                // PageHelper count wrapper: SELECT count(0) FROM (original) table_count
                SelectBody inner = ((SubSelect) plainSelect.getFromItem()).getSelectBody();
                return addToBody(inner, rules, annotationAliases, subject, parameter, denyWhenEmpty);
            }
            return addToPlainSelect(plainSelect, rules, annotationAliases, subject, parameter, denyWhenEmpty);
        }
        if (body instanceof SetOperationList) {
            boolean changed = false;
            for (SelectBody selectBody : ((SetOperationList) body).getSelects()) {
                changed |= addToBody(selectBody, rules, annotationAliases, subject, parameter, denyWhenEmpty);
            }
            return changed;
        }
        return false;
    }

    private static boolean addToPlainSelect(PlainSelect plainSelect,
                                            List<DataPermissionRule> rules,
                                            Map<String, String> annotationAliases,
                                            DataPermissionSubject subject,
                                            Object parameter,
                                            boolean denyWhenEmpty) {
        Set<String> tables = extractTables(plainSelect);
        Map<String, String> actualAliases = extractAliases(plainSelect);
        Map<String, String> aliases = mergeAliases(actualAliases, annotationAliases);

        Map<String, List<DataPermissionRule>> rulesByTable = new LinkedHashMap<String, List<DataPermissionRule>>();
        for (DataPermissionRule rule : rules) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            String table = normalize(rule.getTableName());
            if (table.isEmpty() || !tables.contains(table)) {
                continue;
            }
            List<DataPermissionRule> list = rulesByTable.get(table);
            if (list == null) {
                list = new ArrayList<DataPermissionRule>();
                rulesByTable.put(table, list);
            }
            list.add(rule);
        }

        Expression permission = null;
        for (Map.Entry<String, List<DataPermissionRule>> entry : rulesByTable.entrySet()) {
            String table = entry.getKey();
            String alias = aliases.get(table);
            if (alias == null || alias.isEmpty()) {
                alias = table;
            }
            Expression condition = buildTableCondition(entry.getValue(), alias, aliases, subject, parameter);
            permission = permission == null
                    ? condition
                    : new AndExpression(new Parenthesis(permission), new Parenthesis(condition));
        }

        if (permission == null) {
            if (!denyWhenEmpty) {
                return false;
            }
            permission = falseExpression();
        }

        Expression where = plainSelect.getWhere();
        plainSelect.setWhere(where == null
                ? permission
                : new AndExpression(new Parenthesis(permission), new Parenthesis(where)));
        return true;
    }

    private static Set<String> extractTables(PlainSelect plainSelect) {
        Set<String> tables = new LinkedHashSet<String>();
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table) {
            tables.add(normalize(((Table) fromItem).getName()));
        }
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                FromItem item = join.getRightItem();
                if (item instanceof Table) {
                    tables.add(normalize(((Table) item).getName()));
                }
            }
        }
        return tables;
    }

    private static Map<String, String> extractAliases(PlainSelect plainSelect) {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem instanceof Table) {
            putAlias(aliases, (Table) fromItem);
        }
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                FromItem item = join.getRightItem();
                if (item instanceof Table) {
                    putAlias(aliases, (Table) item);
                }
            }
        }
        return aliases;
    }

    private static void putAlias(Map<String, String> aliases, Table table) {
        String name = normalize(table.getName());
        String alias = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        aliases.put(name, alias);
    }

    private static Map<String, String> mergeAliases(Map<String, String> actualAliases,
                                                    Map<String, String> annotationAliases) {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        if (actualAliases != null) {
            for (Map.Entry<String, String> entry : actualAliases.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    aliases.put(normalize(entry.getKey()), entry.getValue());
                }
            }
        }
        if (annotationAliases != null) {
            for (Map.Entry<String, String> entry : annotationAliases.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && !entry.getValue().trim().isEmpty()) {
                    aliases.put(normalize(entry.getKey()), entry.getValue().trim());
                }
            }
        }
        return aliases;
    }

    private static Expression buildTableCondition(List<DataPermissionRule> rules,
                                                  String alias,
                                                  Map<String, String> aliases,
                                                  DataPermissionSubject subject,
                                                  Object parameter) {
        List<DataPermissionRule> sorted = new ArrayList<DataPermissionRule>(rules);
        Collections.sort(sorted, new Comparator<DataPermissionRule>() {
            @Override
            public int compare(DataPermissionRule left, DataPermissionRule right) {
                int priority = Integer.compare(safe(right.getPriority()), safe(left.getPriority()));
                if (priority != 0) {
                    return priority;
                }
                int order = Integer.compare(safe(left.getSortOrder()), safe(right.getSortOrder()));
                if (order != 0) {
                    return order;
                }
                String leftId = left.getId() == null ? "" : left.getId();
                String rightId = right.getId() == null ? "" : right.getId();
                return leftId.compareTo(rightId);
            }
        });

        Expression combined = null;
        for (DataPermissionRule rule : sorted) {
            Expression current = buildRuleCondition(rule, alias, aliases, subject, parameter);
            if (current == null) {
                current = falseExpression();
            }
            if (combined == null) {
                combined = current;
            } else {
                combined = combine(combined, current, rule.getLogicOperator());
            }
        }
        return combined == null ? falseExpression() : new Parenthesis(combined);
    }

    private static Expression buildRuleCondition(DataPermissionRule rule,
                                                 String alias,
                                                 Map<String, String> aliases,
                                                 DataPermissionSubject subject,
                                                 Object parameter) {
        if (rule.getRuleMode() == RuleMode.COMPLEX
                && rule.getItems() != null
                && !rule.getItems().isEmpty()) {
            return buildComplexCondition(rule, alias, aliases, subject, parameter);
        }
        return buildSimpleCondition(rule, alias, aliases, subject, parameter);
    }

    private static Expression buildSimpleCondition(DataPermissionRule rule,
                                                   String alias,
                                                   Map<String, String> aliases,
                                                   DataPermissionSubject subject,
                                                   Object parameter) {
        ConditionOperator operator = ConditionOperator.from(rule.getConditionType());
        if (isBlank(rule.getFieldName()) || operator == null) {
            log.warn("Data permission rule [{}] has an invalid field or operator", rule.getId());
            return null;
        }
        Column column = createColumn(rule.getFieldName(), alias, aliases);
        if (column == null) {
            log.warn("Data permission rule [{}] has an invalid column: {}", rule.getId(), rule.getFieldName());
            return null;
        }
        if (operator == ConditionOperator.IS_NULL) {
            return new IsNullExpression().withLeftExpression(column);
        }
        if (operator == ConditionOperator.IS_NOT_NULL) {
            return new IsNullExpression().withNot(true).withLeftExpression(column);
        }
        Object value = resolveValue(rule.getConditionValue(), subject, parameter);
        if (value == UNRESOLVED) {
            log.warn("Data permission rule [{}] cannot resolve value: {}", rule.getId(), rule.getConditionValue());
            return null;
        }
        if (operator == ConditionOperator.IN) {
            return buildIn(column, value);
        }
        if (operator == ConditionOperator.LIKE) {
            return new LikeExpression()
                    .withLeftExpression(column)
                    .withRightExpression(new StringValue("%" + escape(String.valueOf(value)) + "%"));
        }
        return buildBinary(column, createValueExpression(value), operator);
    }

    private static Expression buildComplexCondition(DataPermissionRule rule,
                                                    String alias,
                                                    Map<String, String> aliases,
                                                    DataPermissionSubject subject,
                                                    Object parameter) {
        List<DataPermissionRuleItem> items = new ArrayList<DataPermissionRuleItem>(rule.getItems());
        Collections.sort(items, new Comparator<DataPermissionRuleItem>() {
            @Override
            public int compare(DataPermissionRuleItem left, DataPermissionRuleItem right) {
                int group = Integer.compare(safe(left.getGroupNo()), safe(right.getGroupNo()));
                if (group != 0) {
                    return group;
                }
                int subGroup = Integer.compare(safe(left.getSubGroupNo()), safe(right.getSubGroupNo()));
                if (subGroup != 0) {
                    return subGroup;
                }
                return Integer.compare(safe(left.getSortOrder()), safe(right.getSortOrder()));
            }
        });

        Map<Integer, List<DataPermissionRuleItem>> groups =
                new LinkedHashMap<Integer, List<DataPermissionRuleItem>>();
        for (DataPermissionRuleItem item : items) {
            Integer groupNo = item.getGroupNo() == null ? Integer.valueOf(1) : item.getGroupNo();
            List<DataPermissionRuleItem> group = groups.get(groupNo);
            if (group == null) {
                group = new ArrayList<DataPermissionRuleItem>();
                groups.put(groupNo, group);
            }
            group.add(item);
        }

        List<Expression> groupExpressions = new ArrayList<Expression>();
        for (List<DataPermissionRuleItem> group : groups.values()) {
            Map<Integer, List<DataPermissionRuleItem>> subGroups =
                    new LinkedHashMap<Integer, List<DataPermissionRuleItem>>();
            for (DataPermissionRuleItem item : group) {
                Integer subGroupNo = item.getSubGroupNo() == null ? Integer.valueOf(1) : item.getSubGroupNo();
                List<DataPermissionRuleItem> subGroup = subGroups.get(subGroupNo);
                if (subGroup == null) {
                    subGroup = new ArrayList<DataPermissionRuleItem>();
                    subGroups.put(subGroupNo, subGroup);
                }
                subGroup.add(item);
            }

            List<Expression> subExpressions = new ArrayList<Expression>();
            for (List<DataPermissionRuleItem> subGroup : subGroups.values()) {
                Expression subExpression = null;
                for (DataPermissionRuleItem item : subGroup) {
                    Expression current = buildItemCondition(item, alias, aliases, subject, parameter);
                    if (current == null) {
                        current = falseExpression();
                    }
                    if (subExpression == null) {
                        subExpression = current;
                    } else {
                        subExpression = combine(subExpression, current, item.getLogicOperator());
                    }
                }
                if (subExpression != null) {
                    subExpressions.add(new Parenthesis(subExpression));
                }
            }

            if (!subExpressions.isEmpty()) {
                Expression groupExpression = subExpressions.get(0);
                for (int i = 1; i < subExpressions.size(); i++) {
                    groupExpression = new AndExpression(groupExpression, subExpressions.get(i));
                }
                groupExpressions.add(new Parenthesis(groupExpression));
            }
        }

        if (groupExpressions.isEmpty()) {
            return null;
        }
        Expression result = groupExpressions.get(0);
        for (int i = 1; i < groupExpressions.size(); i++) {
            result = new OrExpression(result, groupExpressions.get(i));
        }
        return new Parenthesis(result);
    }

    private static Expression buildItemCondition(DataPermissionRuleItem item,
                                                 String alias,
                                                 Map<String, String> aliases,
                                                 DataPermissionSubject subject,
                                                 Object parameter) {
        ConditionOperator operator = ConditionOperator.from(item.getOperator());
        if (operator == null) {
            log.warn("Data permission rule item [{}] has an invalid operator: {}", item.getId(), item.getOperator());
            return null;
        }
        Column left = createColumn(item.getLeftExpr(), alias, aliases);
        if (left == null) {
            log.warn("Data permission rule item [{}] has an invalid left expression: {}", item.getId(), item.getLeftExpr());
            return null;
        }
        if (operator == ConditionOperator.IS_NULL) {
            return new IsNullExpression().withLeftExpression(left);
        }
        if (operator == ConditionOperator.IS_NOT_NULL) {
            return new IsNullExpression().withNot(true).withLeftExpression(left);
        }

        RightValueType rightType = item.getRightType() == null ? RightValueType.CONST : item.getRightType();
        if (rightType == RightValueType.SUBQUERY) {
            if (operator != ConditionOperator.IN) throw new DataPermissionException("SUBQUERY requires IN");
            Map<String, Object> bindings = BINDINGS.get();
            if (bindings == null) throw new DataPermissionException("SUBQUERY requires applyPrepared and parameter binding");
            String query = SubqueryTemplate.compile(item.getRightExpr(), subject, bindings);
            try {
                return CCJSqlParserUtil.parseCondExpression(left.toString() + " IN (" + query + ")");
            } catch (JSQLParserException ex) { throw new DataPermissionException("Invalid SUBQUERY condition", ex); }
        }
        if (rightType == RightValueType.FIELD) {
            Column right = createColumn(item.getRightExpr(), alias, aliases);
            if (right == null) {
                log.warn("Data permission rule item [{}] has an invalid right expression: {}", item.getId(), item.getRightExpr());
                return null;
            }
            return buildBinary(left, right, operator);
        }

        Object value;
        if (rightType == RightValueType.CURRENT_USER_FIELD) {
            value = subject == null ? null : subject.getField(item.getRightExpr());
            if (value == null) {
                log.warn("Data permission rule item [{}] cannot resolve user field: {}", item.getId(), item.getRightExpr());
                return null;
            }
        } else if (rightType == RightValueType.NONE) {
            return null;
        } else {
            value = resolveValue(item.getRightExpr(), subject, parameter);
            if (value == UNRESOLVED) {
                log.warn("Data permission rule item [{}] cannot resolve value: {}", item.getId(), item.getRightExpr());
                return null;
            }
        }

        if (operator == ConditionOperator.IN) {
            return buildIn(left, value);
        }
        if (operator == ConditionOperator.LIKE) {
            return new LikeExpression()
                    .withLeftExpression(left)
                    .withRightExpression(new StringValue("%" + escape(String.valueOf(value)) + "%"));
        }
        return buildBinary(left, createValueExpression(value), operator);
    }

    private static Object resolveValue(String raw, DataPermissionSubject subject, Object parameter) {
        if (raw == null) {
            return UNRESOLVED;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return UNRESOLVED;
        }
        Matcher matcher = DYNAMIC_VALUE.matcher(value);
        if (matcher.matches()) {
            Object resolved = resolveField(matcher.group(1), subject, parameter);
            return resolved == null ? UNRESOLVED : resolved;
        }
        if (IDENTIFIER.matcher(value).matches()) {
            Object resolved = resolveField(value, subject, parameter);
            // A bare word that is not a user field is a literal such as PENDING.
            return resolved == null ? value : resolved;
        }
        return value;
    }

    private static Object resolveField(String name, DataPermissionSubject subject, Object parameter) {
        Object value = subject == null ? null : subject.getField(name);
        if (value == null && parameter != null) {
            value = Reflections.getProperty(parameter, name);
        }
        return value;
    }

    private static Expression buildIn(Column column, Object value) {
        List<Expression> values = new ArrayList<Expression>();
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                values.add(createValueExpression(item));
            }
        } else {
            String text = String.valueOf(value);
            String[] parts = text.split(",");
            for (String part : parts) {
                String item = stripQuotes(part.trim());
                if (!item.isEmpty()) {
                    values.add(createValueExpression(item));
                }
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        InExpression in = new InExpression();
        in.setLeftExpression(column);
        ExpressionList expressionList = new ExpressionList();
        expressionList.setExpressions(values);
        in.setRightItemsList(expressionList);
        return in;
    }

    private static Expression buildBinary(Expression left, Expression right, ConditionOperator operator) {
        switch (operator) {
            case EQ:
                return new EqualsTo().withLeftExpression(left).withRightExpression(right);
            case NE:
                return new NotEqualsTo().withLeftExpression(left).withRightExpression(right);
            case GT:
                return new GreaterThan().withLeftExpression(left).withRightExpression(right);
            case GE:
                return new GreaterThanEquals().withLeftExpression(left).withRightExpression(right);
            case LT:
                return new MinorThan().withLeftExpression(left).withRightExpression(right);
            case LE:
                return new MinorThanEquals().withLeftExpression(left).withRightExpression(right);
            default:
                return null;
        }
    }

    private static Expression combine(Expression left, Expression right, LogicOperator operator) {
        if (operator == LogicOperator.OR) {
            return new Parenthesis(new OrExpression(left, right));
        }
        return new Parenthesis(new AndExpression(left, right));
    }

    private static Expression createValueExpression(Object value) {
        if (value == null) {
            return new StringValue("NULL");
        }
        if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
            return new DoubleValue(value.toString());
        }
        if (value instanceof Number) {
            return new LongValue(value.toString());
        }
        if (value instanceof Boolean) {
            return new LongValue(Boolean.TRUE.equals(value) ? "1" : "0");
        }
        if (value instanceof Date) {
            return new StringValue(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format((Date) value));
        }
        return new StringValue(escape(String.valueOf(value)));
    }

    private static Column createColumn(String expression, String alias, Map<String, String> aliases) {
        if (isBlank(expression)) {
            return null;
        }
        String raw = expression.trim().replace("`", "").replace("\"", "");
        if (raw.contains(".")) {
            String[] parts = raw.split("\\.", 2);
            if (parts.length != 2 || !isIdentifier(parts[0]) || !isIdentifier(parts[1])) {
                return null;
            }
            String mapped = aliases == null ? null : aliases.get(normalize(parts[0]));
            if (mapped != null && !mapped.isEmpty()) {
                return new Column(mapped + "." + parts[1]);
            }
            return new Column(parts[0] + "." + parts[1]);
        }
        if (!isIdentifier(raw)) {
            return null;
        }
        return new Column(alias + "." + raw);
    }

    private static Expression falseExpression() {
        return new EqualsTo()
                .withLeftExpression(new LongValue(1))
                .withRightExpression(new LongValue(0));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim()
                .replace("`", "")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "");
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}

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
import net.sf.jsqlparser.expression.AnyComparisonExpression;
import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.OracleHierarchicalExpression;
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
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.expression.operators.relational.NamedExpressionList;
import net.sf.jsqlparser.expression.operators.relational.NotEqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Database;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.ExpressionListItem;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.FunctionItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesisFromItem;
import net.sf.jsqlparser.statement.select.Pivot;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import net.sf.jsqlparser.statement.select.SpecialSubSelect;
import net.sf.jsqlparser.statement.select.SubJoin;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.Top;
import net.sf.jsqlparser.statement.select.UnPivot;
import net.sf.jsqlparser.statement.select.ValuesList;
import net.sf.jsqlparser.statement.select.WithItem;
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
        return addToWithItems(select.getWithItemsList(), select.getSelectBody(),
                rules, annotationAliases, subject, parameter, denyWhenEmpty,
                Collections.<String>emptySet());
    }

    /**
     * Rewrites one SQL scope: every WITH item, then the body that can see all
     * of them. A CTE name is visible to later items and to the main body, but
     * not inside its own definition unless it is recursive.
     */
    private static boolean addToWithItems(List<WithItem> withItems,
                                          SelectBody body,
                                          List<DataPermissionRule> rules,
                                          Map<String, String> annotationAliases,
                                          DataPermissionSubject subject,
                                          Object parameter,
                                          boolean denyWhenEmpty,
                                          Set<String> outerCteNames) {
        boolean changed = false;
        Set<String> visibleCtes = new LinkedHashSet<String>();
        if (outerCteNames != null) {
            visibleCtes.addAll(outerCteNames);
        }
        boolean recursiveWith = false;
        if (withItems != null) {
            for (WithItem withItem : withItems) {
                if (withItem != null && withItem.isRecursive()) {
                    recursiveWith = true;
                    break;
                }
            }
        }
        if (withItems != null) {
            for (WithItem withItem : withItems) {
                if (withItem == null) {
                    continue;
                }
                String name = normalize(withItem.getName());
                Set<String> itemVisible = new LinkedHashSet<String>(visibleCtes);
                // JSQLParser 4.6 marks only the first item of a WITH RECURSIVE
                // clause, so treat every item as potentially self-referencing.
                if (recursiveWith && !name.isEmpty()) {
                    itemVisible.add(name);
                }
                if (withItem.getSubSelect() != null) {
                    changed |= addToSubSelect(withItem.getSubSelect(),
                            rules, annotationAliases, subject, parameter, denyWhenEmpty, itemVisible);
                }
                changed |= scanItemsList(withItem.getItemsList(),
                        rules, annotationAliases, subject, parameter, denyWhenEmpty, itemVisible);
                if (!name.isEmpty()) {
                    visibleCtes.add(name);
                }
            }
        }
        changed |= addToBody(body,
                rules, annotationAliases, subject, parameter, denyWhenEmpty, visibleCtes);
        return changed;
    }

    private static boolean addToSubSelect(SubSelect subSelect,
                                          List<DataPermissionRule> rules,
                                          Map<String, String> annotationAliases,
                                          DataPermissionSubject subject,
                                          Object parameter,
                                          boolean denyWhenEmpty,
                                          Set<String> outerCteNames) {
        if (subSelect == null) {
            return false;
        }
        return addToWithItems(subSelect.getWithItemsList(), subSelect.getSelectBody(),
                rules, annotationAliases, subject, parameter, denyWhenEmpty, outerCteNames);
    }

    /**
     * A CTE body can be a VALUES list rather than a subquery; those expressions
     * can still contain subqueries that must be rewritten.
     */
    private static boolean scanItemsList(ItemsList items,
                                         List<DataPermissionRule> rules,
                                         Map<String, String> annotationAliases,
                                         DataPermissionSubject subject,
                                         Object parameter,
                                         boolean denyWhenEmpty,
                                         Set<String> cteNames) {
        if (items == null) {
            return false;
        }
        SubqueryScan scan = new SubqueryScan();
        SubqueryExpressionVisitor visitor = new SubqueryExpressionVisitor(
                rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames, scan);
        scanItems(items, visitor);
        return scan.changed;
    }

    private static void scanItems(ItemsList items, SubqueryExpressionVisitor visitor) {
        if (items instanceof ExpressionList) {
            scanExpressions(((ExpressionList) items).getExpressions(), visitor);
        } else if (items instanceof MultiExpressionList) {
            List<ExpressionList> lists = ((MultiExpressionList) items).getExpressionLists();
            if (lists != null) {
                for (ExpressionList list : lists) {
                    scanItems(list, visitor);
                }
            }
        } else if (items instanceof NamedExpressionList) {
            scanExpressions(((NamedExpressionList) items).getExpressions(), visitor);
        }
    }

    private static void scanExpressions(List<Expression> expressions,
                                        SubqueryExpressionVisitor visitor) {
        if (expressions == null) {
            return;
        }
        for (Expression expression : expressions) {
            visitor.scan(expression);
        }
    }

    private static boolean addToBody(SelectBody body,
                                     List<DataPermissionRule> rules,
                                     Map<String, String> annotationAliases,
                                     DataPermissionSubject subject,
                                     Object parameter,
                                     boolean denyWhenEmpty,
                                     Set<String> cteNames) {
        if (body instanceof PlainSelect) {
            return addToPlainSelect((PlainSelect) body,
                    rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
        }
        if (body instanceof SetOperationList) {
            SetOperationList setOperationList = (SetOperationList) body;
            boolean changed = false;
            if (setOperationList.getSelects() != null) {
                for (SelectBody selectBody : setOperationList.getSelects()) {
                    changed |= addToBody(selectBody,
                            rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
                }
            }
            SubqueryScan scan = new SubqueryScan();
            SubqueryExpressionVisitor visitor = new SubqueryExpressionVisitor(
                    rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames, scan);
            if (setOperationList.getOrderByElements() != null) {
                for (OrderByElement orderBy : setOperationList.getOrderByElements()) {
                    visitor.scan(orderBy.getExpression());
                }
            }
            if (setOperationList.getLimit() != null) {
                visitor.scan(setOperationList.getLimit().getOffset());
                visitor.scan(setOperationList.getLimit().getRowCount());
            }
            if (setOperationList.getOffset() != null) {
                visitor.scan(setOperationList.getOffset().getOffset());
            }
            changed |= scan.changed;
            return changed;
        }
        return false;
    }

    private static boolean addToPlainSelect(PlainSelect plainSelect,
                                            List<DataPermissionRule> rules,
                                            Map<String, String> annotationAliases,
                                            DataPermissionSubject subject,
                                            Object parameter,
                                            boolean denyWhenEmpty,
                                            Set<String> cteNames) {
        QueryScope scope = buildScope(plainSelect, annotationAliases, cteNames);
        Map<String, List<DataPermissionRule>> rulesByTable = groupRules(rules, scope, cteNames);

        Expression permission = null;
        for (Map.Entry<String, List<DataPermissionRule>> entry : rulesByTable.entrySet()) {
            for (TableInstance instance : scope.instances) {
                if (!instance.tableName.equals(entry.getKey())
                        || !isPhysical(instance, cteNames)) {
                    continue;
                }
                // A self-join has one instance per alias: every instance must be
                // filtered, otherwise the duplicated table can leak rows.
                Expression condition = buildTableCondition(entry.getValue(), instance, scope,
                        subject, parameter);
                permission = permission == null
                        ? condition
                        : new AndExpression(new Parenthesis(permission), new Parenthesis(condition));
            }
        }

        boolean changed = permission != null;
        if (permission != null) {
            Expression where = plainSelect.getWhere();
            plainSelect.setWhere(where == null
                    ? permission
                    : new AndExpression(new Parenthesis(permission), new Parenthesis(where)));
        }

        SubqueryScan scan = new SubqueryScan();
        processSubqueries(plainSelect, rules, annotationAliases, subject, parameter,
                denyWhenEmpty, cteNames, scan);
        changed |= scan.changed;

        if (denyWhenEmpty) {
            // A physical table without a rule still needs the outer deny, even
            // when a subquery was rewritten. Opaque sources (table functions)
            // cannot be filtered, so they stay denied even when another table
            // in the scope matched a rule. Scopes with no physical source
            // (constants, CTE references, derived tables) expose no rows of
            // their own and pass through.
            if (scope.opaque || (permission == null && hasPhysicalTables(scope, cteNames))) {
                Expression where = plainSelect.getWhere();
                Expression denied = falseExpression();
                plainSelect.setWhere(where == null
                        ? denied
                        : new AndExpression(new Parenthesis(denied), new Parenthesis(where)));
            }
            if (permission == null) {
                changed = true;
            }
        }
        return changed;
    }

    private static QueryScope buildScope(PlainSelect plainSelect,
                                         Map<String, String> annotationAliases,
                                         Set<String> cteNames) {
        QueryScope scope = new QueryScope();
        addScopeTable(plainSelect.getFromItem(), scope, null);
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                addScopeTable(join.getRightItem(), scope, null);
            }
        }
        applyAnnotationAliases(scope, annotationAliases, cteNames);
        return scope;
    }

    private static void addScopeTable(FromItem fromItem, QueryScope scope, String outerAlias) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            if (isDual(table)) {
                // DUAL is a constant pseudo-table, not a row source to protect.
                return;
            }
            boolean explicitAlias = table.getAlias() != null
                    || (outerAlias != null && !outerAlias.trim().isEmpty());
            String alias = table.getAlias() != null
                    ? table.getAlias().getName()
                    : defaultQualifier(table);
            if (outerAlias != null && !outerAlias.trim().isEmpty()) {
                alias = outerAlias;
            }
            scope.add(new TableInstance(normalize(table.getName()), alias, explicitAlias,
                    isQualified(table)));
        } else if (fromItem instanceof ParenthesisFromItem) {
            ParenthesisFromItem parenthesis = (ParenthesisFromItem) fromItem;
            String alias = parenthesis.getAlias() != null
                    ? parenthesis.getAlias().getName()
                    : outerAlias;
            addScopeTable(parenthesis.getFromItem(), scope, alias);
        } else if (fromItem instanceof SubJoin) {
            SubJoin subJoin = (SubJoin) fromItem;
            // The SubJoin alias names the derived join result, not the inner
            // tables, so their own aliases must be preserved.
            addScopeTable(subJoin.getLeft(), scope, null);
            List<Join> subJoins = subJoin.getJoinList();
            if (subJoins != null) {
                for (Join join : subJoins) {
                    addScopeTable(join.getRightItem(), scope, null);
                }
            }
        } else if (fromItem instanceof SubSelect || fromItem instanceof SpecialSubSelect) {
            // Derived table: subqueries are handled by scanFromItem; there is
            // no physical table in this scope.
        } else if (fromItem instanceof TableFunction) {
            // A table function is itself a row source that cannot be filtered;
            // keep it fail-closed under the default deny policy.
            scope.opaque = true;
        } else if (fromItem instanceof ValuesList) {
            // VALUES is a constant source; subqueries in its rows are handled
            // by scanFromItem.
        } else if (fromItem != null) {
            throw new DataPermissionException("Unsupported FROM item for data permission: "
                    + fromItem.getClass().getName());
        }
    }

    private static void applyAnnotationAliases(QueryScope scope,
                                               Map<String, String> annotationAliases,
                                               Set<String> cteNames) {
        if (annotationAliases == null || annotationAliases.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : annotationAliases.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getValue().trim().isEmpty()) {
                continue;
            }
            String table = normalize(entry.getKey());
            List<TableInstance> instances = physicalInstances(scope.byTable.get(table), cteNames);
            if (instances.isEmpty()) {
                continue;
            }
            if (instances.size() > 1) {
                throw new DataPermissionException("Data permission tableAlias for table '" + table
                        + "' is ambiguous: the table appears " + instances.size()
                        + " times in the same query");
            }
            TableInstance instance = instances.get(0);
            String override = entry.getValue().trim();
            if (instance.explicitAlias && normalize(override).equals(normalize(instance.alias))) {
                instance.effectiveAlias = override;
                scope.byAlias.put(normalize(override), instance);
            } else if (!instance.explicitAlias && normalize(override).equals(table)) {
                // The override names the table itself; the parsed qualifier is
                // already correct and keeps any schema/dblink prefix.
            } else {
                // Applying an alias the query does not define would either make
                // the SQL invalid or attach the predicate to another table.
                log.warn("Ignoring data permission tableAlias '{}:{}': table '{}' is referenced as '{}'",
                        entry.getKey(), override, table, instance.alias);
            }
        }
    }

    private static Map<String, List<DataPermissionRule>> groupRules(List<DataPermissionRule> rules,
                                                                    QueryScope scope,
                                                                    Set<String> cteNames) {
        Map<String, List<DataPermissionRule>> rulesByTable =
                new LinkedHashMap<String, List<DataPermissionRule>>();
        if (rules == null) {
            return rulesByTable;
        }
        Set<String> physicalTables = new LinkedHashSet<String>();
        for (TableInstance instance : scope.instances) {
            if (isPhysical(instance, cteNames)) {
                physicalTables.add(instance.tableName);
            }
        }
        for (DataPermissionRule rule : rules) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            String table = normalize(rule.getTableName());
            if (table.isEmpty() || !physicalTables.contains(table)) {
                continue;
            }
            List<DataPermissionRule> list = rulesByTable.get(table);
            if (list == null) {
                list = new ArrayList<DataPermissionRule>();
                rulesByTable.put(table, list);
            }
            list.add(rule);
        }
        return rulesByTable;
    }

    private static boolean hasPhysicalTables(QueryScope scope, Set<String> cteNames) {
        for (TableInstance instance : scope.instances) {
            if (isPhysical(instance, cteNames)) {
                return true;
            }
        }
        return false;
    }

    private static List<TableInstance> physicalInstances(List<TableInstance> instances,
                                                         Set<String> cteNames) {
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }
        List<TableInstance> physical = new ArrayList<TableInstance>(instances.size());
        for (TableInstance instance : instances) {
            if (isPhysical(instance, cteNames)) {
                physical.add(instance);
            }
        }
        return physical;
    }

    private static boolean isPhysical(TableInstance instance, Set<String> cteNames) {
        // A schema-qualified table is a physical table even when an unqualified
        // CTE of the same short name is visible in the same scope.
        return instance.qualified || cteNames == null || !cteNames.contains(instance.tableName);
    }

    private static boolean isQualified(Table table) {
        if (!isBlank(table.getSchemaName())) {
            return true;
        }
        Database database = table.getDatabase();
        if (database != null && !isBlank(database.getDatabaseName())) {
            return true;
        }
        List<String> parts = table.getNameParts();
        if (parts != null) {
            // A database-only name (db..project) has three parts.
            if (parts.size() > 1) {
                return true;
            }
        }
        if (isDBLink(table)) {
            return true;
        }
        String name = table.getName();
        return name != null && name.indexOf('.') >= 0;
    }

    private static String defaultQualifier(Table table) {
        if (!isQualified(table)) {
            return table.getName();
        }
        if (isDBLink(table)) {
            // Oracle accepts @link only on the object name, not in a column
            // qualifier; the implicit correlation name is the bare table name.
            return table.getName();
        }
        return table.getFullyQualifiedName();
    }

    private static boolean isDBLink(Table table) {
        List<String> parts = table.getNameParts();
        if (parts != null) {
            for (String part : parts) {
                if (part != null && part.indexOf('@') >= 0) {
                    return true;
                }
            }
        }
        String name = table.getName();
        return name != null && name.indexOf('@') >= 0;
    }

    private static boolean isDual(Table table) {
        return !isQualified(table) && "dual".equals(normalize(table.getName()));
    }

    private static void processSubqueries(PlainSelect plainSelect,
                                          List<DataPermissionRule> rules,
                                          Map<String, String> annotationAliases,
                                          DataPermissionSubject subject,
                                          Object parameter,
                                          boolean denyWhenEmpty,
                                          Set<String> cteNames,
                                          SubqueryScan scan) {
        SubqueryExpressionVisitor visitor = new SubqueryExpressionVisitor(
                rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames, scan);
        scanPivot(plainSelect.getFromItem(), visitor);
        scanFromItem(plainSelect.getFromItem(), visitor, rules, annotationAliases,
                subject, parameter, denyWhenEmpty, cteNames, scan);
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                scanPivot(join.getRightItem(), visitor);
                scanFromItem(join.getRightItem(), visitor, rules, annotationAliases,
                        subject, parameter, denyWhenEmpty, cteNames, scan);
                if (join.getOnExpressions() != null) {
                    for (Expression onExpression : join.getOnExpressions()) {
                        visitor.scan(onExpression);
                    }
                }
            }
        }
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem selectItem : plainSelect.getSelectItems()) {
                if (selectItem instanceof SelectExpressionItem) {
                    visitor.scan(((SelectExpressionItem) selectItem).getExpression());
                }
            }
        }
        visitor.scan(plainSelect.getWhere());
        visitor.scan(plainSelect.getHaving());
        if (plainSelect.getGroupBy() != null
                && plainSelect.getGroupBy().getGroupByExpressions() != null) {
            for (Expression expression : plainSelect.getGroupBy().getGroupByExpressions()) {
                visitor.scan(expression);
            }
        }
        if (plainSelect.getOrderByElements() != null) {
            for (OrderByElement orderBy : plainSelect.getOrderByElements()) {
                visitor.scan(orderBy.getExpression());
            }
        }
        Distinct distinct = plainSelect.getDistinct();
        if (distinct != null && distinct.getOnSelectItems() != null) {
            for (SelectItem selectItem : distinct.getOnSelectItems()) {
                if (selectItem instanceof SelectExpressionItem) {
                    visitor.scan(((SelectExpressionItem) selectItem).getExpression());
                }
            }
        }
        if (plainSelect.getTop() != null) {
            visitor.scan(plainSelect.getTop().getExpression());
        }
        OracleHierarchicalExpression hierarchical = plainSelect.getOracleHierarchical();
        if (hierarchical != null) {
            visitor.scan(hierarchical.getStartExpression());
            visitor.scan(hierarchical.getConnectExpression());
        }
        if (plainSelect.getGroupBy() != null && plainSelect.getGroupBy().getGroupingSets() != null) {
            for (Object groupingSet : plainSelect.getGroupBy().getGroupingSets()) {
                if (groupingSet instanceof ExpressionList) {
                    scanExpressions(((ExpressionList) groupingSet).getExpressions(), visitor);
                }
            }
        }
        if (plainSelect.getLimit() != null) {
            visitor.scan(plainSelect.getLimit().getOffset());
            visitor.scan(plainSelect.getLimit().getRowCount());
        }
        if (plainSelect.getOffset() != null) {
            visitor.scan(plainSelect.getOffset().getOffset());
        }
    }

    private static void scanFromItem(FromItem fromItem,
                                     SubqueryExpressionVisitor visitor,
                                     List<DataPermissionRule> rules,
                                     Map<String, String> annotationAliases,
                                     DataPermissionSubject subject,
                                     Object parameter,
                                     boolean denyWhenEmpty,
                                     Set<String> cteNames,
                                     SubqueryScan scan) {
        if (fromItem instanceof SubSelect) {
            scan.changed |= addToSubSelect((SubSelect) fromItem,
                    rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
        } else if (fromItem instanceof SpecialSubSelect) {
            SubSelect subSelect = ((SpecialSubSelect) fromItem).getSubSelect();
            if (subSelect != null) {
                scan.changed |= addToSubSelect(subSelect,
                        rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
            }
        } else if (fromItem instanceof TableFunction) {
            visitor.scan(((TableFunction) fromItem).getFunction());
        } else if (fromItem instanceof ValuesList) {
            scanItems(((ValuesList) fromItem).getMultiExpressionList(), visitor);
        } else if (fromItem instanceof SubJoin) {
            SubJoin subJoin = (SubJoin) fromItem;
            scanPivot(subJoin.getLeft(), visitor);
            scanFromItem(subJoin.getLeft(), visitor, rules, annotationAliases,
                    subject, parameter, denyWhenEmpty, cteNames, scan);
            List<Join> subJoins = subJoin.getJoinList();
            if (subJoins != null) {
                for (Join join : subJoins) {
                    scanPivot(join.getRightItem(), visitor);
                    scanFromItem(join.getRightItem(), visitor, rules, annotationAliases,
                            subject, parameter, denyWhenEmpty, cteNames, scan);
                    if (join.getOnExpressions() != null) {
                        for (Expression onExpression : join.getOnExpressions()) {
                            visitor.scan(onExpression);
                        }
                    }
                }
            }
        } else if (fromItem instanceof ParenthesisFromItem) {
            scanFromItem(((ParenthesisFromItem) fromItem).getFromItem(), visitor,
                    rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames, scan);
        }
    }

    private static void scanPivot(FromItem fromItem, SubqueryExpressionVisitor visitor) {
        if (fromItem instanceof Table) {
            Table table = (Table) fromItem;
            scanPivot(table.getPivot(), table.getUnPivot(), visitor);
        } else if (fromItem instanceof SubSelect) {
            SubSelect subSelect = (SubSelect) fromItem;
            scanPivot(subSelect.getPivot(), subSelect.getUnPivot(), visitor);
        } else if (fromItem instanceof ValuesList) {
            ValuesList values = (ValuesList) fromItem;
            scanPivot(values.getPivot(), values.getUnPivot(), visitor);
        }
    }

    private static void scanPivot(Pivot pivot, UnPivot unPivot, SubqueryExpressionVisitor visitor) {
        if (pivot != null) {
            if (pivot.getFunctionItems() != null) {
                for (FunctionItem item : pivot.getFunctionItems()) {
                    visitor.scan(item.getFunction());
                }
            }
            if (pivot.getSingleInItems() != null) {
                for (SelectExpressionItem item : pivot.getSingleInItems()) {
                    visitor.scan(item.getExpression());
                }
            }
            if (pivot.getMultiInItems() != null) {
                for (ExpressionListItem item : pivot.getMultiInItems()) {
                    if (item.getExpressionList() != null) {
                        scanExpressions(item.getExpressionList().getExpressions(), visitor);
                    }
                }
            }
        }
        if (unPivot != null && unPivot.getUnPivotInClause() != null) {
            for (SelectExpressionItem item : unPivot.getUnPivotInClause()) {
                visitor.scan(item.getExpression());
            }
        }
    }

    private static Expression buildTableCondition(List<DataPermissionRule> rules,
                                                  TableInstance instance,
                                                  QueryScope scope,
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
            Expression current = buildRuleCondition(rule, instance, scope, subject, parameter);
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
                                                 TableInstance instance,
                                                 QueryScope scope,
                                                 DataPermissionSubject subject,
                                                 Object parameter) {
        if (rule.getRuleMode() == RuleMode.COMPLEX
                && rule.getItems() != null
                && !rule.getItems().isEmpty()) {
            return buildComplexCondition(rule, instance, scope, subject, parameter);
        }
        return buildSimpleCondition(rule, instance, scope, subject, parameter);
    }

    private static Expression buildSimpleCondition(DataPermissionRule rule,
                                                   TableInstance instance,
                                                   QueryScope scope,
                                                   DataPermissionSubject subject,
                                                   Object parameter) {
        ConditionOperator operator = ConditionOperator.from(rule.getConditionType());
        if (isBlank(rule.getFieldName()) || operator == null) {
            log.warn("Data permission rule [{}] has an invalid field or operator", rule.getId());
            return null;
        }
        Column column = createColumn(rule.getFieldName(), instance, scope, true);
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
                                                    TableInstance instance,
                                                    QueryScope scope,
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
                    Expression current = buildItemCondition(item, instance, scope, subject, parameter);
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
                                                 TableInstance instance,
                                                 QueryScope scope,
                                                 DataPermissionSubject subject,
                                                 Object parameter) {
        ConditionOperator operator = ConditionOperator.from(item.getOperator());
        if (operator == null) {
            log.warn("Data permission rule item [{}] has an invalid operator: {}", item.getId(), item.getOperator());
            return null;
        }
        Column left = createColumn(item.getLeftExpr(), instance, scope, true);
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
        if (rightType == RightValueType.FIELD) {
            Column right = createColumn(item.getRightExpr(), instance, scope, false);
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
        String literal = unquoteLiteral(value);
        if (literal != null) {
            // A single quoted constant is never resolved as a user field:
            // 'PENDING' means the string PENDING, even if a field has that name.
            return literal;
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
                String trimmed = part.trim();
                String item = unquoteLiteral(trimmed);
                if (item == null) {
                    item = trimmed;
                }
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

    private static Column createColumn(String expression, TableInstance instance, QueryScope scope,
                                       boolean leftSide) {
        if (isBlank(expression)) {
            return null;
        }
        String raw = expression.trim().replace("`", "").replace("\"", "");
        if (raw.contains(".")) {
            String[] parts = raw.split("\\.", 2);
            if (parts.length != 2 || !isIdentifier(parts[0]) || !isIdentifier(parts[1])) {
                return null;
            }
            String qualifier = normalize(parts[0]);
            TableInstance aliased = scope == null ? null : scope.byAlias.get(qualifier);
            if (aliased != null) {
                if (instance != null && aliased.tableName.equals(instance.tableName)) {
                    // The qualifier names this table's alias; expand it per
                    // instance so a self-join filters every alias.
                    return new Column(instance.effectiveAlias + "." + parts[1]);
                }
                if (leftSide) {
                    // A rule's left field must belong to the protected table;
                    // binding it to another relation would filter that table
                    // instead and leave the protected one open.
                    return null;
                }
                // An explicit alias of another table wins; it may refer to an
                // outer scope.
                return new Column(parts[0] + "." + parts[1]);
            }
            if (scope != null) {
                List<TableInstance> instances = scope.byTable.get(qualifier);
                if (instances != null && !instances.isEmpty()) {
                    if (instance != null && instance.tableName.equals(qualifier)) {
                        return new Column(instance.effectiveAlias + "." + parts[1]);
                    }
                    if (leftSide) {
                        return null;
                    }
                    if (instances.size() == 1) {
                        return new Column(instances.get(0).effectiveAlias + "." + parts[1]);
                    }
                    throw new DataPermissionException("Ambiguous table reference '" + parts[0]
                            + "' in data permission rule");
                }
            }
            if (leftSide) {
                return null;
            }
            return new Column(parts[0] + "." + parts[1]);
        }
        if (!isIdentifier(raw) || instance == null) {
            return null;
        }
        return new Column(instance.effectiveAlias + "." + raw);
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

    /**
     * Returns the unquoted content when {@code value} is exactly one quoted
     * literal; returns {@code null} for anything else (for example an IN list
     * such as {@code 'A','B'}, which is split by the caller).
     */
    private static String unquoteLiteral(String value) {
        if (value == null || value.length() < 2) {
            return null;
        }
        char quote = value.charAt(0);
        if (quote != '\'' && quote != '"') {
            return null;
        }
        StringBuilder result = new StringBuilder(value.length() - 2);
        for (int i = 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == quote) {
                if (i + 1 < value.length() && value.charAt(i + 1) == quote) {
                    result.append(quote);
                    i++;
                } else if (i == value.length() - 1) {
                    return result.toString();
                } else {
                    return null;
                }
            } else {
                result.append(current);
            }
        }
        return null;
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

    private static final class TableInstance {
        private final String tableName;
        private final String alias;
        private final boolean explicitAlias;
        private final boolean qualified;
        private String effectiveAlias;

        private TableInstance(String tableName, String alias, boolean explicitAlias,
                              boolean qualified) {
            this.tableName = tableName;
            this.alias = alias == null || alias.trim().isEmpty() ? tableName : alias;
            this.effectiveAlias = this.alias;
            this.explicitAlias = explicitAlias;
            this.qualified = qualified;
        }
    }

    private static final class QueryScope {
        private final List<TableInstance> instances = new ArrayList<TableInstance>();
        private boolean opaque;
        private final Map<String, List<TableInstance>> byTable =
                new LinkedHashMap<String, List<TableInstance>>();
        private final Map<String, TableInstance> byAlias =
                new LinkedHashMap<String, TableInstance>();

        private void add(TableInstance instance) {
            instances.add(instance);
            List<TableInstance> tableInstances = byTable.get(instance.tableName);
            if (tableInstances == null) {
                tableInstances = new ArrayList<TableInstance>();
                byTable.put(instance.tableName, tableInstances);
            }
            tableInstances.add(instance);
            byAlias.put(normalize(instance.alias), instance);
            byAlias.put(normalize(instance.effectiveAlias), instance);
        }
    }

    private static final class SubqueryScan {
        private boolean changed;
    }

    private static final class SubqueryExpressionVisitor extends ExpressionVisitorAdapter {
        private final List<DataPermissionRule> rules;
        private final Map<String, String> annotationAliases;
        private final DataPermissionSubject subject;
        private final Object parameter;
        private final boolean denyWhenEmpty;
        private final Set<String> cteNames;
        private final SubqueryScan scan;

        private SubqueryExpressionVisitor(List<DataPermissionRule> rules,
                                          Map<String, String> annotationAliases,
                                          DataPermissionSubject subject,
                                          Object parameter,
                                          boolean denyWhenEmpty,
                                          Set<String> cteNames,
                                          SubqueryScan scan) {
            this.rules = rules;
            this.annotationAliases = annotationAliases;
            this.subject = subject;
            this.parameter = parameter;
            this.denyWhenEmpty = denyWhenEmpty;
            this.cteNames = cteNames;
            this.scan = scan;
        }

        private void scan(Expression expression) {
            if (expression != null) {
                expression.accept(this);
            }
        }

        @Override
        public void visit(SubSelect subSelect) {
            scan.changed |= addToSubSelect(subSelect,
                    rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
        }

        @Override
        public void visit(AnyComparisonExpression anyComparison) {
            // ExpressionVisitorAdapter does not descend into ANY/ALL/SOME.
            if (anyComparison.isUsingSubSelect()) {
                SubSelect subSelect = anyComparison.getSubSelect();
                if (subSelect != null) {
                    scan.changed |= addToSubSelect(subSelect,
                            rules, annotationAliases, subject, parameter, denyWhenEmpty, cteNames);
                }
            } else {
                scanItems(anyComparison.getItemsList(), this);
            }
        }
    }
}

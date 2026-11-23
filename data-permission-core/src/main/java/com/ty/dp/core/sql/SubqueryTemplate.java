package com.ty.dp.core.sql;

import com.ty.dp.core.exception.DataPermissionException;
import com.ty.dp.core.subject.DataPermissionSubject;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import java.util.*;
import java.util.regex.*;

/** Strict read-only SQL templates. No request parameters or arbitrary property paths. */
public final class SubqueryTemplate {
    public static final int MAX_LENGTH = 4000;
    public static final Set<String> FIELDS = Collections.unmodifiableSet(new LinkedHashSet<String>(
            Arrays.asList("userId", "deptId", "username", "userName", "userNo", "roleIds", "visibleTaskIds", "visibleProjectIds")));
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{([A-Za-z][A-Za-z0-9]*)}");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|merge|replace|create|alter|drop|truncate|into|outfile|dumpfile|lock|unlock|call|execute|exec|grant|revoke|set|load|procedure|for|next|nextval|currval)\\b");
    private static final Pattern CALL = Pattern.compile("(?i)\\b([a-z_][a-z0-9_]*)\\s*\\(");
    private static final Set<String> SAFE_FUNCTIONS = new HashSet<String>(Arrays.asList(
            "count", "min", "max", "sum", "avg", "coalesce", "ifnull", "nullif", "lower", "upper", "trim", "cast"));
    private static final Set<String> SAFE_CALLS = new HashSet<String>(Arrays.asList(
            "in", "exists", "and", "or", "not", "as", "select", "where", "on", "having", "over",
            "count", "min", "max", "sum", "avg", "coalesce", "ifnull", "nullif", "lower", "upper", "trim", "cast"));
    private SubqueryTemplate() { }

    public static void validate(String template) { compile(template, null, null); }

    public static String compile(String template, DataPermissionSubject subject, Map<String, Object> bindings) {
        if (template == null || template.trim().isEmpty() || template.length() > MAX_LENGTH)
            throw error("SQL is required and must be at most " + MAX_LENGTH + " characters");
        // Reject comments even in literals to make the template policy unambiguous.
        if (template.contains("--") || template.contains("/*") || template.contains("*/") || template.contains(";")
                || template.contains("@") || template.contains(":" ) || template.contains("?") || template.contains("${"))
            throw error("comments, multiple statements, variables and raw parameters are forbidden");
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        int count = 0;
        while (matcher.find()) {
            String field = matcher.group(1);
            if (!FIELDS.contains(field)) throw error("unknown placeholder: " + field);
            String token = "__dp_sq_" + (bindings == null ? count : bindings.size());
            Object value = subject == null ? null : ("roleIds".equals(field) ? subject.getRoleIds() : subject.getField(field));
            if (subject != null && "userId".equals(field) && value == null) value = subject.getSubjectId();
            if (bindings != null && value == null) throw error("unresolved user field: " + field);
            List<Object> values = new ArrayList<Object>();
            if (value instanceof Collection) values.addAll((Collection<?>) value);
            else values.add(value);
            if (bindings != null && (values.isEmpty() || values.contains(null))) throw error("empty user field: " + field);
            if (values.isEmpty()) values.add(null);
            StringBuilder replacement = new StringBuilder();
            for (Object element : values) {
                if (replacement.length() > 0) replacement.append(", ");
                String name = token + "_" + replacement.length();
                replacement.append(":" + name);
                if (bindings != null) bindings.put(name, element);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
            count++;
        }
        matcher.appendTail(result);
        String sql = result.toString();
        if (sql.contains("#")) throw error("invalid placeholder or comment");
        String code = SqlParameterTokens.codeOnly(sql);
        if (code.contains("#") || code.contains("{") || code.contains("}") || code.contains("\\"))
            throw error("invalid placeholder or SQL token");
        // Placeholders inside string/identifier literals are never bindable.
        if (occurrences(code, ":__dp_sq_") != occurrences(sql, ":__dp_sq_"))
            throw error("placeholders must be SQL values, outside quotes");
        if (FORBIDDEN.matcher(code).find()) throw error("only read-only SELECT is allowed");
        if (Pattern.compile("[\"`]\\s*\\(").matcher(sql).find()
                || Pattern.compile("\\.\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(").matcher(code).find())
            throw error("quoted or qualified function calls are forbidden");
        Matcher calls = CALL.matcher(code);
        while (calls.find()) if (!SAFE_CALLS.contains(calls.group(1).toLowerCase(Locale.ROOT)))
            throw error("function is not on the read-only whitelist: " + calls.group(1));
        try {
            net.sf.jsqlparser.statement.Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) throw error("a single SELECT is required");
            StringBuilder buffer = new StringBuilder();
            net.sf.jsqlparser.util.deparser.ExpressionDeParser expressions =
                    new net.sf.jsqlparser.util.deparser.ExpressionDeParser() {
                @Override public void visit(net.sf.jsqlparser.expression.Function function) {
                    String name = function.getName().toLowerCase(Locale.ROOT);
                    if (!SAFE_FUNCTIONS.contains(name)) throw error("function is not on the read-only whitelist: " + name);
                    super.visit(function);
                }
            };
            net.sf.jsqlparser.util.deparser.SelectDeParser selects =
                    new net.sf.jsqlparser.util.deparser.SelectDeParser(expressions, buffer);
            expressions.setSelectVisitor(selects);
            expressions.setBuffer(buffer);
            statement.accept(new net.sf.jsqlparser.util.deparser.StatementDeParser(expressions, selects, buffer));
        } catch (net.sf.jsqlparser.JSQLParserException ex) { throw error("invalid SELECT: " + ex.getMessage()); }
        return sql;
    }
    private static int occurrences(String s, String token) { return (s.length() - s.replace(token, "").length()) / token.length(); }
    private static DataPermissionException error(String message) { return new DataPermissionException("SUBQUERY: " + message); }
}

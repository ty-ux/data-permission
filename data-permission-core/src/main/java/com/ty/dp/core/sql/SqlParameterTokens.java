package com.ty.dp.core.sql;

import java.util.function.Function;

/** Lexical parameter traversal preserves quoted literals and MyBatis parameter order. */
public final class SqlParameterTokens {
    private SqlParameterTokens() { }
    public static String codeOnly(String sql) { return scan(sql, null, true); }
    public static String replace(String sql, Function<String, String> replacer) { return scan(sql, replacer, false); }
    private static String scan(String sql, Function<String, String> replacer, boolean mask) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sql.length();) {
            char c = sql.charAt(i);
            int end = i + 1;
            if (c == '\'' || c == '"' || c == '`') {
                char quote = c;
                while (end < sql.length()) {
                    char next = sql.charAt(end++);
                    if (next == '\\' && end < sql.length()) { end++; continue; }
                    if (next == quote) {
                        if (end < sql.length() && sql.charAt(end) == quote) { end++; continue; }
                        break;
                    }
                }
                out.append(mask ? spaces(end-i) : sql.substring(i,end));
            } else if ((c == '-' && end < sql.length() && sql.charAt(end) == '-') || c == '#'
                    && !(end < sql.length() && sql.charAt(end) == '{')) {
                while (end < sql.length() && sql.charAt(end) != '\n') end++;
                out.append(mask ? spaces(end-i) : sql.substring(i,end));
            } else if (c == '/' && end < sql.length() && sql.charAt(end) == '*') {
                int close = sql.indexOf("*/", end+1); end = close < 0 ? sql.length() : close+2;
                out.append(mask ? spaces(end-i) : sql.substring(i,end));
            } else if (!mask && replacer != null && (c == '?' || c == ':' && sql.startsWith("__dp_",end))) {
                if (c == ':') while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) end++;
                out.append(replacer.apply(sql.substring(i,end)));
            } else out.append(c);
            i = end;
        }
        return out.toString();
    }
    private static String spaces(int n) { StringBuilder b = new StringBuilder(); while(n-- > 0) b.append(' '); return b.toString(); }
}

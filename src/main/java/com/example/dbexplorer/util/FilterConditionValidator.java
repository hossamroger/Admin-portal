package com.example.dbexplorer.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defense-in-depth validator for admin-authored row-level filter conditions
 * (stored in DBX_USER_TABLE_FILTERS and ANDed raw into query WHERE clauses).
 *
 * <p>These conditions are intentionally free-form SQL fragments writable only by
 * an ADMIN, so the primary trust boundary is the admin role. This validator adds
 * a second layer: it rejects fragments that try to break out of a boolean WHERE
 * expression into a second statement, a comment, a subquery DML/DDL, or a call to
 * a dangerous package. It is NOT a full SQL parser — it errs on the side of
 * rejecting anything that looks like an attempt to do more than compare columns.
 */
public final class FilterConditionValidator {

    private FilterConditionValidator() {}

    /** Statement / DML / DDL / procedural keywords that have no place in a WHERE filter. */
    private static final Set<String> BLOCKED_KEYWORDS = new HashSet<>(Arrays.asList(
        "SELECT", "INSERT", "UPDATE", "DELETE", "MERGE", "UNION", "INTERSECT", "MINUS",
        "DROP", "ALTER", "CREATE", "TRUNCATE", "RENAME", "COMMENT",
        "GRANT", "REVOKE", "COMMIT", "ROLLBACK", "SAVEPOINT",
        "EXEC", "EXECUTE", "CALL", "DECLARE", "BEGIN", "END",
        "DBMS", "UTL", "SYS", "ADMIN", "INTO", "FROM", "WHERE", "JOIN"
    ));

    /** Identifiers that look like a function/package invocation we should not allow. */
    private static final Pattern DANGEROUS_CALL = Pattern.compile(
        "(?i)\\b(DBMS_|UTL_|SYS\\.|OWA_|HTP\\.|HTF\\.|XMLTYPE|CTXSYS|DBA_|ALL_TAB)");

    private static final Pattern KEYWORD_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final int MAX_LENGTH = 4000;

    /**
     * Validates a single filter condition.
     *
     * @param condition the raw SQL boolean fragment (may be null/blank — treated as "no filter")
     * @throws IllegalArgumentException if the fragment is rejected
     */
    public static void validate(String condition) {
        if (condition == null) return;
        String trimmed = condition.trim();
        if (trimmed.isEmpty()) return;

        if (trimmed.length() > MAX_LENGTH)
            throw new IllegalArgumentException("Filter condition is too long (max " + MAX_LENGTH + " chars).");

        // Statement terminator — would allow stacking a second statement.
        if (trimmed.indexOf(';') >= 0)
            throw new IllegalArgumentException("Filter condition may not contain ';'.");

        // SQL comment markers — would allow truncating the surrounding query.
        if (trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/"))
            throw new IllegalArgumentException("Filter condition may not contain SQL comments.");

        // Quotes must be balanced (Oracle escapes a single quote by doubling it).
        if (countUnescapedQuotes(trimmed) % 2 != 0)
            throw new IllegalArgumentException("Filter condition has unbalanced single quotes.");

        // Parentheses must be balanced.
        if (!parensBalanced(trimmed))
            throw new IllegalArgumentException("Filter condition has unbalanced parentheses.");

        // Strip string literals so values like STATUS = 'INSERT' don't trip keyword checks,
        // then scan the structural remainder for blocked tokens.
        String stripped = stripStringLiterals(trimmed);

        if (DANGEROUS_CALL.matcher(stripped).find())
            throw new IllegalArgumentException("Filter condition references a disallowed package or function.");

        java.util.regex.Matcher m = KEYWORD_TOKEN.matcher(stripped);
        while (m.find()) {
            if (BLOCKED_KEYWORDS.contains(m.group().toUpperCase()))
                throw new IllegalArgumentException("Filter condition may not contain the keyword '" + m.group() + "'.");
        }
    }

    private static int countUnescapedQuotes(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\'') count++;
        }
        return count;
    }

    private static boolean parensBalanced(String s) {
        int depth = 0;
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '(') depth++;
            else if (c == ')') { depth--; if (depth < 0) return false; }
        }
        return depth == 0;
    }

    /** Replaces the contents of every single-quoted literal with empty, leaving structure intact. */
    private static String stripStringLiterals(String s) {
        StringBuilder out = new StringBuilder(s.length());
        boolean inStr = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') { inStr = !inStr; continue; }
            if (!inStr) out.append(c);
        }
        return out.toString();
    }
}

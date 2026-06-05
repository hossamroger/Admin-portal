package com.example.dbexplorer.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Pragmatic SQL script splitter.
 *
 * Rules:
 *  - If the whole script looks like a single PL/SQL unit (BEGIN/DECLARE block or
 *    CREATE [OR REPLACE] PROCEDURE/FUNCTION/PACKAGE/TRIGGER/TYPE), it is treated as
 *    ONE statement (a trailing "/" terminator is stripped).
 *  - Otherwise, statements are separated by ";", while ignoring semicolons that
 *    appear inside single-quoted strings, line comments and block comments.
 */
public final class SqlSplitter {

    private SqlSplitter() {}

    public static List<String> split(String script) {
        List<String> out = new ArrayList<>();
        if (script == null) return out;
        String trimmed = script.trim();
        if (trimmed.isEmpty()) return out;

        if (looksLikePlSql(trimmed)) {
            // remove a trailing slash terminator if present
            String body = trimmed;
            if (body.endsWith("/")) body = body.substring(0, body.length() - 1).trim();
            out.add(body);
            return out;
        }

        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inLineComment = false, inBlockComment = false;
        char[] c = script.toCharArray();
        for (int i = 0; i < c.length; i++) {
            char ch = c[i];
            char next = i + 1 < c.length ? c[i + 1] : '\0';

            if (inLineComment) {
                cur.append(ch);
                if (ch == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                cur.append(ch);
                if (ch == '*' && next == '/') { cur.append(next); i++; inBlockComment = false; }
                continue;
            }
            if (inSingle) {
                cur.append(ch);
                if (ch == '\'') inSingle = false;
                continue;
            }
            // not in any special region
            if (ch == '-' && next == '-') { cur.append(ch); inLineComment = true; continue; }
            if (ch == '/' && next == '*') { cur.append(ch); inBlockComment = true; continue; }
            if (ch == '\'') { cur.append(ch); inSingle = true; continue; }
            if (ch == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) out.add(s);
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) out.add(tail);
        return out;
    }

    private static boolean looksLikePlSql(String s) {
        String u = s.toUpperCase();
        if (u.startsWith("BEGIN") || u.startsWith("DECLARE")) return true;
        if (u.startsWith("CREATE")) {
            // strip "CREATE OR REPLACE"
            String rest = u.replaceFirst("^CREATE\\s+(OR\\s+REPLACE\\s+)?", "");
            return rest.startsWith("PROCEDURE") || rest.startsWith("FUNCTION")
                    || rest.startsWith("PACKAGE") || rest.startsWith("TRIGGER")
                    || rest.startsWith("TYPE");
        }
        return false;
    }
}

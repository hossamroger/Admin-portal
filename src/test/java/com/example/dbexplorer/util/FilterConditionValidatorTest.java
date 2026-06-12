package com.example.dbexplorer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterConditionValidatorTest {

    // ── Legitimate filters that must pass ──────────────────────────────────────

    @Test void allowsSimpleEquality() {
        assertDoesNotThrow(() -> FilterConditionValidator.validate("PLATFORM_CODE = 'AQARI'"));
    }

    @Test void allowsNumericComparison() {
        assertDoesNotThrow(() -> FilterConditionValidator.validate("ORDER_C > 3"));
    }

    @Test void allowsInList() {
        assertDoesNotThrow(() -> FilterConditionValidator.validate("STATUS IN ('A', 'I')"));
    }

    @Test void allowsAndOrWithParens() {
        assertDoesNotThrow(() ->
            FilterConditionValidator.validate("(PLATFORM_CODE = 'AQARI' AND STATUS = 'A') OR ORDER_C >= 10"));
    }

    @Test void allowsValueContainingBlockedKeywordInsideLiteral() {
        // 'INSERT' is a string value here, not a statement
        assertDoesNotThrow(() -> FilterConditionValidator.validate("ACTION_NAME = 'INSERT'"));
    }

    @Test void allowsColumnWhoseNameContainsKeyword() {
        // CREATED_AT contains "CREATE" but is a distinct identifier
        assertDoesNotThrow(() -> FilterConditionValidator.validate("CREATED_AT IS NOT NULL"));
    }

    @Test void allowsEscapedQuoteInLiteral() {
        assertDoesNotThrow(() -> FilterConditionValidator.validate("NAME = 'O''Brien'"));
    }

    @Test void allowsNullAndBlank() {
        assertDoesNotThrow(() -> FilterConditionValidator.validate(null));
        assertDoesNotThrow(() -> FilterConditionValidator.validate("   "));
    }

    // ── Injection attempts that must be rejected ───────────────────────────────

    @Test void rejectsStatementTerminator() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1; DROP TABLE DBX_USERS"));
    }

    @Test void rejectsInlineComment() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1 -- ignore the rest"));
    }

    @Test void rejectsBlockComment() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1 /* sneaky */"));
    }

    @Test void rejectsUnionSubquery() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1 UNION SELECT PASSWORD FROM DBX_USERS"));
    }

    @Test void rejectsSubquerySelect() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("ID IN (SELECT USERNAME FROM DBX_USERS)"));
    }

    @Test void rejectsDangerousPackageCall() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1 AND DBMS_LOCK.SLEEP(5) = 0"));
    }

    @Test void rejectsUnbalancedQuotes() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("NAME = 'unterminated"));
    }

    @Test void rejectsUnbalancedParens() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("(STATUS = 'A'"));
    }

    @Test void rejectsDelete() {
        assertThrows(IllegalArgumentException.class,
            () -> FilterConditionValidator.validate("1=1 OR DELETE"));
    }
}

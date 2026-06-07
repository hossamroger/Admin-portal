package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import com.example.dbexplorer.dto.QueryDtos.ColumnFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for the parameterized column-filter WHERE building. */
class QueryServiceFilterTest {

    private QueryService service;

    @BeforeEach
    void setUp() {
        DataSource ds = mock(DataSource.class);
        AppProperties props = new AppProperties();
        SchemaService schema = mock(SchemaService.class);
        when(schema.getColumnNames("EMP"))
            .thenReturn(new LinkedHashSet<>(Arrays.asList("ID", "NAME", "SALARY")));
        service = new QueryService(ds, props, schema);
    }

    private ColumnFilter filter(String col, String op, String val) {
        ColumnFilter f = new ColumnFilter();
        f.column = col;
        f.operator = op;
        f.value = val;
        return f;
    }

    @Test
    void noFiltersProducesEmptyClause() {
        QueryService.WhereClause w = service.buildWhere("EMP", null, Collections.emptyList());
        assertThat(w.sql).isEmpty();
        assertThat(w.binds).isEmpty();
    }

    @Test
    void equalsFilterIsParameterized() {
        QueryService.WhereClause w =
            service.buildWhere("EMP", null, Collections.singletonList(filter("NAME", "EQ", "Alice")));
        assertThat(w.sql).isEqualTo(" WHERE \"NAME\" = ?");
        assertThat(w.binds).containsExactly("Alice");
    }

    @Test
    void containsWrapsValueWithWildcardsAndUppercases() {
        QueryService.WhereClause w =
            service.buildWhere("EMP", null, Collections.singletonList(filter("NAME", "CONTAINS", "li")));
        assertThat(w.sql).isEqualTo(" WHERE UPPER(\"NAME\") LIKE ?");
        assertThat(w.binds).containsExactly("%LI%");
    }

    @Test
    void multipleFiltersAreAndedTogether() {
        List<ColumnFilter> filters = Arrays.asList(
            filter("NAME", "STARTS", "A"),
            filter("SALARY", "GTE", "1000"));
        QueryService.WhereClause w = service.buildWhere("EMP", null, filters);
        assertThat(w.sql).isEqualTo(" WHERE UPPER(\"NAME\") LIKE ? AND \"SALARY\" >= ?");
        assertThat(w.binds).containsExactly("A%", "1000");
    }

    @Test
    void nullOperatorTakesNoBind() {
        QueryService.WhereClause w =
            service.buildWhere("EMP", null, Collections.singletonList(filter("NAME", "NULL", "ignored")));
        assertThat(w.sql).isEqualTo(" WHERE \"NAME\" IS NULL");
        assertThat(w.binds).isEmpty();
    }

    @Test
    void tableFilterIsCombinedWithColumnFilters() {
        QueryService.WhereClause w =
            service.buildWhere("EMP", "DEPT = 10", Collections.singletonList(filter("NAME", "EQ", "Bob")));
        assertThat(w.sql).isEqualTo(" WHERE (DEPT = 10) AND \"NAME\" = ?");
        assertThat(w.binds).containsExactly("Bob");
    }

    @Test
    void unknownColumnIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            service.buildWhere("EMP", null, Collections.singletonList(filter("DROP TABLE", "EQ", "x"))));
    }

    @Test
    void unknownOperatorIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
            service.buildWhere("EMP", null, Collections.singletonList(filter("NAME", "OR 1=1", "x"))));
    }

    @Test
    void injectionInValueStaysAsBindNotSql() {
        QueryService.WhereClause w = service.buildWhere("EMP", null,
            Collections.singletonList(filter("NAME", "EQ", "x' OR '1'='1")));
        // The malicious value is bound, never concatenated into SQL.
        assertThat(w.sql).isEqualTo(" WHERE \"NAME\" = ?");
        assertThat(w.binds).containsExactly("x' OR '1'='1");
    }
}

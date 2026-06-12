package com.example.dbexplorer.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcConfigTest {

    @Test
    void appliesConfiguredTimeoutToJdbcTemplate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppProperties props = new AppProperties();
        props.setQueryTimeoutSeconds(42);

        new JdbcConfig(jdbc, props).applyQueryTimeout();

        verify(jdbc).setQueryTimeout(42);
    }

    @Test
    void doesNotSetTimeoutWhenZero() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppProperties props = new AppProperties();
        props.setQueryTimeoutSeconds(0);

        new JdbcConfig(jdbc, props).applyQueryTimeout();

        verify(jdbc, never()).setQueryTimeout(anyInt());
    }
}

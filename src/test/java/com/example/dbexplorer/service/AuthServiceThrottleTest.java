package com.example.dbexplorer.service;

import com.example.dbexplorer.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceThrottleTest {

    @Test
    void locksAccountAfterFiveFailedAttempts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // user lookup always fails -> every attempt is a failed login
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any()))
            .thenThrow(new RuntimeException("no such user"));
        AuthService auth = new AuthService(new AppProperties(), jdbc);

        for (int i = 0; i < 5; i++) {
            assertFalse(auth.authenticate("eve", "wrong").isPresent());
        }
        // sixth attempt is locked out, even with correct-looking input
        assertThrows(SecurityException.class, () -> auth.authenticate("eve", "wrong"));
        // a different user is unaffected
        assertFalse(auth.authenticate("bob", "wrong").isPresent());
    }
}

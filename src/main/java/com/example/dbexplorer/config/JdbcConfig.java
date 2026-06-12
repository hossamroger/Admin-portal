package com.example.dbexplorer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.PostConstruct;

/**
 * Applies the configured {@code dbexplorer.query-timeout-seconds} to the
 * auto-configured {@link JdbcTemplate} so every plain JdbcTemplate query
 * inherits a statement timeout. (QueryService sets the timeout on its own
 * Statements explicitly; this covers everything else, e.g. GenericCrudService.)
 *
 * When the configured value is {@code <= 0} no timeout is applied — Spring's
 * default of "no timeout" is preserved.
 */
@Configuration
public class JdbcConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcConfig.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties props;

    @Autowired
    public JdbcConfig(JdbcTemplate jdbcTemplate, AppProperties props) {
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    @PostConstruct
    public void applyQueryTimeout() {
        int seconds = props.getQueryTimeoutSeconds();
        if (seconds > 0) {
            jdbcTemplate.setQueryTimeout(seconds);
            LOG.info("Applied default JdbcTemplate query timeout of {}s", seconds);
        }
    }
}

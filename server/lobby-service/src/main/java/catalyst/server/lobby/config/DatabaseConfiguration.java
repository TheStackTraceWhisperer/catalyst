package catalyst.server.lobby.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

@Slf4j
@Factory
@RequiredArgsConstructor
public class DatabaseConfiguration {

    private final ServerProperties props;

    @Singleton
    DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getDbUrl());
        config.setUsername(props.getDbUser());
        config.setPassword(props.getDbPassword());
        config.setMaximumPoolSize(props.getDbPoolMaxSize());
        config.setMinimumIdle(props.getDbPoolMinIdle());
        config.setConnectionTimeout(props.getDbConnectionTimeoutMs());
        config.setPoolName("catalyst-server");
        log.info("Creating DB pool: url={} user={}", props.getDbUrl(), props.getDbUser());
        return new HikariDataSource(config);
    }
}

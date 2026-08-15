package com.cinemetrics.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class ClickHouseConfig {

    @Value("${clickhouse.url}")
    private String url;

    @Value("${clickhouse.username}")
    private String username;

    @Value("${clickhouse.password}")
    private String password;

    @Value("${clickhouse.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${clickhouse.socket-timeout:30000}")
    private int socketTimeout;

    @Bean
    public DataSource clickHouseDataSource() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("connect_timeout", String.valueOf(connectionTimeout));
        props.setProperty("socket_timeout", String.valueOf(socketTimeout));
        // SSL is required for ClickHouse Cloud
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "STRICT");
        return new ClickHouseDataSource(url, props);
    }
}

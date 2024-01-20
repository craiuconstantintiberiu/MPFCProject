package com.example.data;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class JdbcTemplateConfig {

    @Bean
    public JdbcTemplate jdbcTemplateHospitalDB(DataSource hospitalDB) {
        return new JdbcTemplate(hospitalDB);
    }

    @Bean
    public JdbcTemplate jdbcTemplateHospitalAdminDB(DataSource hospitalAdminDB) {
        return new JdbcTemplate(hospitalAdminDB);
    }
}

package com.loopers.infrastructure.pg;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PgConfig {

    @Bean
    public PgRouter pgRouter(List<PgClient> pgClients) {
        return new PgRouter(pgClients);
    }
}

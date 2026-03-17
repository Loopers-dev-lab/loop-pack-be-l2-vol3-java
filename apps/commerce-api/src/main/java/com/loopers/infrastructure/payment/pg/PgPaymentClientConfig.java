package com.loopers.infrastructure.payment.pg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class PgPaymentClientConfig {

    @Bean
    public PgPaymentHttpInterface pgPaymentHttpInterface(
            @Value("${pg.base-url}") String baseUrl
    ) {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(PgPaymentHttpInterface.class);
    }
}

package com.kalshiweather.ingestion.config;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * A scheduled ingestion job talking to three third-party APIs shouldn't be able to hang
 * indefinitely on one of them — every client gets a bounded connect/read timeout.
 *
 * Prototype-scoped to match Spring Boot's own autoconfigured RestClient.Builder bean: each
 * client injects its own builder instance and calls baseUrl(...).build() immediately, so
 * there's no risk of one client's base URL leaking into another's.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public RestClient.Builder restClientBuilder() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings));
    }
}

package com.basiclab.iot.common.security.internal;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;

@AutoConfiguration
@ConditionalOnProperty(prefix = "easyaiot.security.internal", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(InternalServiceAuthProperties.class)
public class InternalServiceAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InternalServiceKeyProvider internalServiceKeyProvider(
            Environment environment, InternalServiceAuthProperties properties) {
        return new EnvironmentInternalServiceKeyProvider(environment, properties.getKeyReferences());
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalServiceAuthNonceStore internalServiceAuthNonceStore(
            StringRedisTemplate redisTemplate, InternalServiceAuthProperties properties) {
        return new RedisInternalServiceAuthNonceStore(redisTemplate, properties.getNonceKeyPrefix());
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalServiceAuthVerifier internalServiceAuthVerifier(
            InternalServiceKeyProvider keyProvider,
            InternalServiceAuthNonceStore nonceStore,
            InternalServiceAuthProperties properties) {
        List<InternalServiceAuthRoute> routes = properties.getRoutes().stream()
                .map(route -> new InternalServiceAuthRoute(route.getMethod(), route.getPath()))
                .collect(Collectors.toList());
        return new InternalServiceAuthVerifier(keyProvider, nonceStore, routes,
                Clock.systemUTC(), properties.getMaxClockSkewSeconds(), properties.getNonceTtlSeconds());
    }
}

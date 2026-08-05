package com.basiclab.iot.common.capability;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/** Auto-configures the single capability source. Missing configuration is fail-closed. */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(CapabilityProperties.class)
public class CapabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CapabilityService.class)
    public CapabilityService capabilityService(CapabilityProperties properties,
                                               ResourceLoader resourceLoader,
                                               ObjectProvider<ObjectMapper> objectMapperProvider) {
        String location = properties.getManifestLocation();
        if (location == null || location.trim().isEmpty()) {
            log.warn("Capability manifest is not configured; power capabilities are disabled");
            return ManifestCapabilityService.disabled(properties.getProfile());
        }
        Resource resource = resourceLoader.getResource(location.trim());
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Capability manifest is not readable: " + location);
        }
        try (java.io.InputStream input = resource.getInputStream()) {
            ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
            ManifestCapabilityService service = ManifestCapabilityService.load(input, mapper);
            if (!"unconfigured".equals(properties.getProfile())
                    && !service.snapshot().getProfile().equals(properties.getProfile())) {
                throw new IllegalStateException("Capability profile does not match manifest: configured="
                        + properties.getProfile() + ", manifest=" + service.snapshot().getProfile());
            }
            return service;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load capability manifest: " + location, error);
        }
    }
}

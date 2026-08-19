package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.security.FileNodeAgentSigningKeyProvider;
import com.basiclab.iot.node.security.NodeAgentRequestSigner;
import com.basiclab.iot.node.security.NodeAgentSigningKeyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** OPEN03-07 的显式装配边界；关闭时不创建服务、job 或轮询入口。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "easyaiot.collector.config-dispatch", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(CollectorConfigDispatchProperties.class)
public class CollectorConfigDispatchConfiguration {

    @Bean
    @ConditionalOnMissingBean(CollectorConfigReleaseClientPort.class)
    public CollectorConfigReleaseClientPort collectorConfigReleaseClient(
            CollectorConfigReleaseInternalApi internalApi) {
        return new CollectorConfigReleaseClientAdapter(internalApi);
    }

    @Bean
    @ConditionalOnMissingBean(CollectorNodeAuthorityPort.class)
    public CollectorNodeAuthorityPort collectorNodeAuthority(ComputeNodeMapper mapper) {
        return new ComputeNodeCollectorAuthorityAdapter(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(NodeAgentSigningKeyProvider.class)
    public NodeAgentSigningKeyProvider nodeAgentSigningKeyProvider(
            CollectorConfigDispatchProperties properties) {
        String signingKeyFile = properties.getSigningKeyFile();
        if (signingKeyFile == null || signingKeyFile.isBlank()) {
            return nodeId -> List.of();
        }
        return new FileNodeAgentSigningKeyProvider(Path.of(signingKeyFile));
    }

    @Bean
    @ConditionalOnMissingBean(NodeAgentRequestSigner.class)
    public NodeAgentRequestSigner nodeAgentRequestSigner(NodeAgentSigningKeyProvider keyProvider) {
        return new NodeAgentRequestSigner(keyProvider);
    }

    @Bean
    @ConditionalOnMissingBean(CollectorAgentPort.class)
    public CollectorAgentPort collectorAgentClient(ObjectMapper objectMapper,
                                                    NodeAgentRequestSigner signer,
                                                    CollectorConfigDispatchProperties properties) {
        return new CollectorAgentClient(objectMapper, signer,
                Duration.ofMillis(properties.getConnectTimeoutMs()),
                Duration.ofMillis(properties.getReadTimeoutMs()));
    }

    @Bean
    @ConditionalOnMissingBean(CollectorConfigDispatchBackoff.class)
    public CollectorConfigDispatchBackoff collectorConfigDispatchBackoff(
            CollectorConfigDispatchProperties properties) {
        return new CollectorConfigDispatchBackoff(Clock.systemUTC(),
                Duration.ofMillis(properties.getBaseDelayMs()),
                Duration.ofMillis(properties.getMaxDelayMs()));
    }

    @Bean
    @ConditionalOnMissingBean(CollectorConfigDispatchService.class)
    public CollectorConfigDispatchService collectorConfigDispatchService(
            CollectorConfigReleaseClientPort releaseClient,
            CollectorNodeAuthorityPort nodeAuthority,
            CollectorAgentPort agentClient,
            CollectorConfigDispatchBackoff backoff) {
        return new CollectorConfigDispatchService(releaseClient, nodeAuthority, agentClient,
                Clock.systemUTC(), backoff);
    }

    @Bean
    @ConditionalOnMissingBean(CollectorConfigDispatchJob.class)
    public CollectorConfigDispatchJob collectorConfigDispatchJob(
            CollectorConfigDispatchService service,
            CollectorConfigDispatchProperties properties) {
        return new CollectorConfigDispatchJob(service, properties.getBatchLimit());
    }
}

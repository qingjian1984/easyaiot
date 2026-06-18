package com.basiclab.iot.sink.consumer;

import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.sink.service.PostProcessService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 后处理结果 Kafka 消费者：异步入库并派发告警
 */
@Slf4j
@Component
public class PostProcessResultConsumer {

    @Autowired
    private PostProcessService postProcessService;

    @KafkaListener(
            topics = "${spring.kafka.post-process.result-topic:iot-post-process-result}",
            groupId = "${spring.kafka.post-process.result-group-id:iot-sink-post-process-result}",
            containerFactory = "iotKafkaListenerContainerFactory"
    )
    public void consumeResult(
            @Payload String messageJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        try {
            if (messageJson == null || messageJson.isEmpty()) {
                ack(acknowledgment);
                return;
            }
            Map<String, Object> message = JsonUtils.parseObject(messageJson, new TypeReference<Map<String, Object>>() {});
            if (message == null || message.isEmpty()) {
                ack(acknowledgment);
                return;
            }
            postProcessService.persistResultAndDispatchAlerts(message);
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("处理后处理结果失败 topic={} partition={} offset={}: {}",
                    topic, partition, offset, e.getMessage(), e);
        }
    }

    private static void ack(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}

package com.basiclab.iot.sink.consumer;

import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.sink.domain.model.PostProcessRequestMessage;
import com.basiclab.iot.sink.service.PostProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 后处理请求 Kafka 消费者：分发至计算节点 Worker 并写入结果主题
 */
@Slf4j
@Component
public class PostProcessRequestConsumer {

    @Autowired
    private PostProcessService postProcessService;

    @KafkaListener(
            topics = "${spring.kafka.post-process.request-topic:iot-post-process-request}",
            groupId = "${spring.kafka.post-process.request-group-id:iot-sink-post-process-request}",
            containerFactory = "iotKafkaListenerContainerFactory"
    )
    public void consumeRequest(
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
            PostProcessRequestMessage message = JsonUtils.parseObject(messageJson, PostProcessRequestMessage.class);
            if (message == null || message.getTaskId() == null) {
                log.warn("后处理请求消息无效 topic={} offset={}", topic, offset);
                ack(acknowledgment);
                return;
            }
            postProcessService.dispatchAndPublishResult(message);
            ack(acknowledgment);
        } catch (Exception e) {
            log.error("处理后处理请求失败 topic={} partition={} offset={}: {}",
                    topic, partition, offset, e.getMessage(), e);
        }
    }

    private static void ack(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}

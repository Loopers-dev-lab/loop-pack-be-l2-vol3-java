package com.loopers.collector.interfaces;

import com.loopers.collector.cdc.CdcConnectCollectorService;
import com.loopers.collector.config.CdcConnectConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CdcConnectCollectorListener {

    private final CdcConnectCollectorService service;

    public CdcConnectCollectorListener(CdcConnectCollectorService service) {
        this.service = service;
    }

    @KafkaListener(
            topicPattern = "${cdc.connect.topic-pattern:^cdc-connect-.*$}",
            groupId = "${cdc.connect.consumer-group:loopers-cdc-connect-consumer}",
            containerFactory = CdcConnectConsumerConfig.CDC_CONNECT_EVENT_LISTENER
    )
    public void onCdcEvents(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        service.process(record);
        acknowledgment.acknowledge();
    }
}

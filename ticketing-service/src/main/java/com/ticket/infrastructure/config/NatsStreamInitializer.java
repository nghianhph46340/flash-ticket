package com.ticket.infrastructure.config;

import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component("natsStreamInitializer")
@RequiredArgsConstructor
public class NatsStreamInitializer {

    private final Connection natsConnection;

    @Value("${nats.stream.name:TICKETS}")
    private String streamName;

    @Value("${nats.stream.subject:tickets.*}")
    private String streamSubject;

    @PostConstruct
    public void initStream() {
        try {
            JetStreamManagement jsm = natsConnection.jetStreamManagement();

            boolean exists = false;
            try {
                StreamInfo info = jsm.getStreamInfo(streamName);
                if (info != null) {
                    exists = true;
                    log.info("NATS Stream '{}' already exists with {} messages.", streamName, info.getStreamState().getMsgCount());
                }
            } catch (Exception ignored) {
                // Stream chưa tồn tại
            }

            if (!exists) {
                try {
                    StreamConfiguration config = StreamConfiguration.builder()
                            .name(streamName)
                            .subjects(streamSubject)
                            .storageType(StorageType.File)
                            .retentionPolicy(RetentionPolicy.Limits)
                            .duplicateWindow(Duration.ofMinutes(3))
                            .build();

                    jsm.addStream(config);
                    log.info("Created NATS Stream '{}' with subject '{}' (StorageType.File).", streamName, streamSubject);
                } catch (Exception fileStorageEx) {
                    log.warn("Failed to create stream with File storage ({}), falling back to Memory storage...", fileStorageEx.getMessage());
                    StreamConfiguration memConfig = StreamConfiguration.builder()
                            .name(streamName)
                            .subjects(streamSubject)
                            .storageType(StorageType.Memory)
                            .retentionPolicy(RetentionPolicy.Limits)
                            .duplicateWindow(Duration.ofMinutes(3))
                            .build();

                    jsm.addStream(memConfig);
                    log.info("Created NATS Stream '{}' with subject '{}' (StorageType.Memory).", streamName, streamSubject);
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize NATS JetStream stream '{}': {}", streamName, e.getMessage(), e);
            throw new IllegalStateException("Cannot start ticketing-service without NATS JetStream initialized", e);
        }
    }
}

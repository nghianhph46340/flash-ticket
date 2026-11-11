package com.ticket.infrastructure.adapter.out.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.core.domain.event.TicketReservedEvent;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class NatsTicketEventPublisher {

    private final Connection natsConnection;
    private final ObjectMapper objectMapper;

    /**
     * Gửi event vé đã được giữ vào NATS JetStream với cơ chế Deduplication native
     */
    public CompletableFuture<PublishAck> publishTicketReservedEvent(TicketReservedEvent event) {
        try {
            JetStream js = natsConnection.jetStream();

            // Deduplication Header: Nếu cùng ID trong khoảng 3 phút, NATS tự động drop để chống trùng
            Headers headers = new Headers();
            headers.add("Nats-Msg-Id", "ORD-RESERVE-" + event.getOrderCode());

            String jsonPayload = objectMapper.writeValueAsString(event);

            io.nats.client.Message msg = NatsMessage.builder()
                    .subject("tickets.reserved")
                    .headers(headers)
                    .data(jsonPayload.getBytes(StandardCharsets.UTF_8))
                    .build();

            log.info("Publishing tickets.reserved for orderCode: {}", event.getOrderCode());
            return js.publishAsync(msg);
        } catch (Exception e) {
            log.error("Failed to publish tickets.reserved event for order: {}", event.getOrderCode(), e);
            CompletableFuture<PublishAck> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}

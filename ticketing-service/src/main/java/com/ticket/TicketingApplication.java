package com.ticket;

import com.ticket.core.domain.event.TicketReservedEvent;
import com.ticket.core.domain.model.enums.*;
import com.ticket.infrastructure.adapter.in.web.dto.*;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.annotation.ImportRuntimeHints;
import com.ticket.infrastructure.config.NativeRuntimeHints;

@SpringBootApplication
@EnableScheduling
@ImportRuntimeHints(NativeRuntimeHints.class)
@RegisterReflectionForBinding({
        ReserveTicketRequest.class,
        ReserveTicketResponse.class,
        TicketReservedEvent.class,
        PaymentWebhookRequest.class,
        PaymentWebhookResponse.class,
        OrderPaymentInfoResponse.class,
        ErrorResponse.class,
        OrderStatus.class,
        PaymentStatus.class,
        SeatStatus.class,
        TicketStatus.class,
        UserRole.class
})
public class TicketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }
}


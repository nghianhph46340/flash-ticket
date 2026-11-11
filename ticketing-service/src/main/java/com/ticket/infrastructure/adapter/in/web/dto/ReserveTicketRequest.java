package com.ticket.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReserveTicketRequest {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "userId is required")
    private String userId;

    @NotEmpty(message = "seatIds cannot be empty")
    @Size(min = 1, max = 4, message = "You can reserve maximum 4 seats per order")
    private List<String> seatIds;
}

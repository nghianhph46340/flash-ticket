package com.ticket.infrastructure.adapter.in.web.dto;

import com.ticket.infrastructure.adapter.out.persistence.entity.AuditoriumEntity;
import com.ticket.infrastructure.adapter.out.persistence.entity.ShowtimeEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ShowtimeResponse {
    private UUID id;
    private UUID movieId;
    private UUID auditoriumId;
    private String auditoriumName;
    private String screenFormat;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal basePrice;
    private String status;

    public static ShowtimeResponse from(ShowtimeEntity s, AuditoriumEntity a) {
        return ShowtimeResponse.builder()
                .id(s.getId())
                .movieId(s.getMovieId())
                .auditoriumId(s.getAuditoriumId())
                .auditoriumName(a != null ? a.getName() : null)
                .screenFormat(a != null ? a.getScreenFormat().name() : null)
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .basePrice(s.getBasePrice())
                .status(s.getStatus().name())
                .build();
    }
}

package com.ticket.infrastructure.adapter.in.web.dto;

import com.ticket.infrastructure.adapter.out.persistence.entity.ConcessionEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ConcessionResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private String category;

    public static ConcessionResponse from(ConcessionEntity e) {
        return ConcessionResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .price(e.getPrice())
                .imageUrl(e.getImageUrl())
                .category(e.getCategory())
                .build();
    }
}

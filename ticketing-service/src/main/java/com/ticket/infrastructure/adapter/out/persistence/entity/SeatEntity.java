package com.ticket.infrastructure.adapter.out.persistence.entity;

import com.ticket.core.domain.model.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatEntity {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "ticket_tier_id")
    private UUID ticketTierId;

    @Column(name = "section", nullable = false, length = 50)
    private String section;

    @Column(name = "row_num", nullable = false, length = 50)
    private String rowNum;

    @Column(name = "seat_num", nullable = false, length = 50)
    private String seatNum;

    @Column(name = "coordinate_x")
    private Double coordinateX;

    @Column(name = "coordinate_y")
    private Double coordinateY;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false)
    private SeatStatus status;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}

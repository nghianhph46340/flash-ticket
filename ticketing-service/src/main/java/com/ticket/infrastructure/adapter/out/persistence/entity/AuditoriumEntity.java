package com.ticket.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auditoriums")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriumEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cinema_id", nullable = false)
    private UUID cinemaId;

    @Column(nullable = false)
    private String name;

    @Column(name = "screen_format", nullable = false)
    @Enumerated(EnumType.STRING)
    private ScreenFormat screenFormat;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "is_active")
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum ScreenFormat {
        STANDARD_2D, STANDARD_3D, IMAX, DOLBY_ATMOS, FOUR_DX
    }
}

package com.ticket.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "cinema_seats")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaSeatsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "auditorium_id", nullable = false)
    private UUID auditoriumId;

    @Column(name = "seat_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SeatType seatType;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(name = "pair_seat_id")
    private UUID pairSeatId;  // null nếu không phải Sweetbox

    @Column(name = "coordinate_x")
    private Double coordinateX;

    @Column(name = "coordinate_y")
    private Double coordinateY;

    @Column(name = "is_active")
    private boolean active = true;

    public enum SeatType {
        STANDARD, VIP, SWEETBOX, BLOCKED
    }
}

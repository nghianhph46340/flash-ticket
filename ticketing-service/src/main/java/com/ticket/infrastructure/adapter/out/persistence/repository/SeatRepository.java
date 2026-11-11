package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.core.domain.model.enums.SeatStatus;
import com.ticket.infrastructure.adapter.out.persistence.entity.SeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<SeatEntity, UUID> {

    List<SeatEntity> findByVenueId(UUID venueId);

    java.util.Optional<SeatEntity> findByVenueIdAndSectionAndRowNumAndSeatNum(
            UUID venueId, String section, String rowNum, String seatNum
    );

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :status, s.reservationExpiresAt = :expiresAt WHERE s.id IN :seatIds")
    int updateStatusForSeats(
            @Param("seatIds") List<UUID> seatIds,
            @Param("status") SeatStatus status,
            @Param("expiresAt") Instant expiresAt
    );
}

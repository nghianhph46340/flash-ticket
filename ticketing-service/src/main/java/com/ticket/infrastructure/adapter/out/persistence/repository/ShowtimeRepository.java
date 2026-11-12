package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.ShowtimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShowtimeRepository extends JpaRepository<ShowtimeEntity, UUID> {

    List<ShowtimeEntity> findByMovieIdAndStatusOrderByStartTimeAsc(UUID movieId, ShowtimeEntity.ShowtimeStatus status);

    // Kiểm tra chống trùng lịch phòng chiếu (overlap prevention)
    @Query("""
        SELECT COUNT(s) > 0 FROM ShowtimeEntity s
        WHERE s.auditoriumId = :auditoriumId
        AND s.status NOT IN ('CANCELLED', 'ARCHIVED')
        AND s.startTime < :endTime
        AND s.endTime > :startTime
        AND (:excludeId IS NULL OR s.id != :excludeId)
    """)
    boolean existsOverlappingShowtime(
        @Param("auditoriumId") UUID auditoriumId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        @Param("excludeId") UUID excludeId
    );
}

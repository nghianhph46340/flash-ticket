package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.CinemaSeatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CinemaSeatsRepository extends JpaRepository<CinemaSeatsEntity, UUID> {
    List<CinemaSeatsEntity> findByAuditoriumIdAndActiveTrue(UUID auditoriumId);
}

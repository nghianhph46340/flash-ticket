package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.AuditoriumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditoriumRepository extends JpaRepository<AuditoriumEntity, UUID> {
    List<AuditoriumEntity> findByCinemaIdAndActiveTrue(UUID cinemaId);
}

package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.CinemaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CinemaRepository extends JpaRepository<CinemaEntity, UUID> {
    List<CinemaEntity> findByActiveTrue();
}

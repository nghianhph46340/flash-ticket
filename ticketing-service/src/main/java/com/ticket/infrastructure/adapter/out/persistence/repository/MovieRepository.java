package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.MovieEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovieRepository extends JpaRepository<MovieEntity, UUID> {
    Optional<MovieEntity> findBySlug(String slug);
    List<MovieEntity> findByActiveTrue();
}

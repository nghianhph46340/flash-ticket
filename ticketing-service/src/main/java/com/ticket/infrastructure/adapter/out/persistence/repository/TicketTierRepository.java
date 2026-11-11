package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.TicketTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketTierRepository extends JpaRepository<TicketTierEntity, UUID> {

    List<TicketTierEntity> findByEventId(UUID eventId);
}

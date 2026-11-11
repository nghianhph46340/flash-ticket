package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByOrderId(UUID orderId);

    Optional<TicketEntity> findByTicketCode(String ticketCode);

    Optional<TicketEntity> findByQrHash(String qrHash);
}

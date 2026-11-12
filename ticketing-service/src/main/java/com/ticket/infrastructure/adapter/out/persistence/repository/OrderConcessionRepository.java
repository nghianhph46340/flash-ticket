package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.infrastructure.adapter.out.persistence.entity.OrderConcessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderConcessionRepository extends JpaRepository<OrderConcessionEntity, UUID> {
    List<OrderConcessionEntity> findByOrderId(UUID orderId);
}

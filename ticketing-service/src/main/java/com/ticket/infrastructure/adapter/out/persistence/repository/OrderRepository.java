package com.ticket.infrastructure.adapter.out.persistence.repository;

import com.ticket.core.domain.model.enums.OrderStatus;
import com.ticket.infrastructure.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    Optional<OrderEntity> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    @Query("SELECT o FROM OrderEntity o WHERE o.status = :status AND o.expiresAt < :now")
    List<OrderEntity> findExpiredOrders(@Param("status") OrderStatus status, @Param("now") Instant now);
}

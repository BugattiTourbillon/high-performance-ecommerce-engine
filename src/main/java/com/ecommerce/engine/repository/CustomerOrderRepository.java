package com.ecommerce.engine.repository;

import com.ecommerce.engine.entity.CustomerOrder;
import com.ecommerce.engine.entity.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "paymentRecord"})
    List<CustomerOrder> findByUserUsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"items", "items.product", "paymentRecord"})
    java.util.Optional<CustomerOrder> findById(Long id);

    Page<CustomerOrder> findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        OrderStatus status,
        Instant start,
        Instant end,
        Pageable pageable
    );
}

package com.ecommerce.engine.repository;

import com.ecommerce.engine.entity.Inventory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i
        from Inventory i
        join fetch i.product p
        where p.id in :productIds
        order by p.id asc
        """)
    List<Inventory> findAllByProductIdsForUpdate(@Param("productIds") Collection<Long> productIds);
}

package com.ecommerce.engine.repository;

import com.ecommerce.engine.entity.Product;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "inventory")
    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("""
        select p
        from Product p
        where p.active = true
        """)
    Page<Product> findActiveProductsRaw(Pageable pageable);

    @EntityGraph(attributePaths = "inventory")
    @Query("""
        select p
        from Product p
        where p.active = true
        order by p.popularityScore desc, p.updatedAt desc
        """)
    List<Product> findPopularProducts(Pageable pageable);

    @Query("""
        select p
        from Product p
        where p.active = true
        order by p.popularityScore desc, p.updatedAt desc
        """)
    List<Product> findPopularProductsRaw(Pageable pageable);

    @EntityGraph(attributePaths = "inventory")
    Optional<Product> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.inventory WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySku(String sku);
}

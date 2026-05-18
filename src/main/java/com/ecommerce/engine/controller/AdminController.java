package com.ecommerce.engine.controller;

import com.ecommerce.engine.dto.order.DailySalesSummaryResponse;
import com.ecommerce.engine.dto.product.InventoryAdjustmentRequest;
import com.ecommerce.engine.dto.product.ProductCreateRequest;
import com.ecommerce.engine.dto.product.ProductResponse;
import com.ecommerce.engine.service.BatchJobService;
import com.ecommerce.engine.service.InventoryService;
import com.ecommerce.engine.service.ProductCatalogService;
import com.ecommerce.engine.service.SalesReportingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ProductCatalogService productCatalogService;
    private final InventoryService inventoryService;
    private final BatchJobService batchJobService;
    private final SalesReportingService salesReportingService;

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody ProductCreateRequest request) {
        return productCatalogService.createProduct(request);
    }

    @PutMapping("/inventory/{productId}")
    public ProductResponse updateInventory(@PathVariable Long productId, @Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.updateInventory(productId, request);
    }

    @PostMapping("/jobs/daily-sales")
    public DailySalesSummaryResponse triggerDailySalesJob(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salesDate
    ) {
        batchJobService.runDailySalesAggregation(salesDate);
        return salesReportingService.getSummary(salesDate);
    }

    @GetMapping("/reports/daily-sales/{salesDate}")
    public DailySalesSummaryResponse getDailySalesSummary(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salesDate
    ) {
        return salesReportingService.getSummary(salesDate);
    }
}

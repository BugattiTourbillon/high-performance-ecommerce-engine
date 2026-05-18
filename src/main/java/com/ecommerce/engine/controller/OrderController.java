package com.ecommerce.engine.controller;

import com.ecommerce.engine.dto.checkout.CheckoutRequest;
import com.ecommerce.engine.dto.order.OrderResponse;
import com.ecommerce.engine.service.CheckoutService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    public OrderResponse checkout(Authentication authentication, @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(authentication.getName(), request);
    }

    @GetMapping
    public List<OrderResponse> listOrders(Authentication authentication) {
        return checkoutService.listOrders(authentication.getName());
    }
}

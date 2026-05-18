# Concurrency And Integrity Notes

## Checkout transaction

The checkout path is the critical ACID boundary.

Inside one `@Transactional` method it performs:

1. Lock customer cart row.
2. Read cart items in deterministic product-id order.
3. Lock all corresponding inventory rows with `PESSIMISTIC_WRITE`.
4. Validate stock.
5. Decrement inventory.
6. Create order and order items.
7. Simulate payment.
8. Persist payment record.
9. Clear cart.
10. Publish an order event that is consumed only after commit.

If any step fails, Spring rolls the transaction back and the stock/order/cart state returns to its previous consistent state.

## Why both pessimistic and optimistic techniques are present

- Pessimistic locking is used on the hottest integrity path: checkout inventory decrements.
- Optimistic locking via `@Version` stays on product, inventory, and cart aggregates so non-checkout update paths still detect stale writes.

## Thread-safe hotspots

### Cart mutation

`CartService` locks the cart row before mutation. This prevents same-user request races across multiple app instances.

### Inventory decrement

`CheckoutService` locks inventory rows in ascending product-id order. That prevents oversell and reduces deadlock risk for overlapping carts.

### Payment resource guard

`PaymentGatewaySimulator` uses a fair `Semaphore` to cap local concurrent payment simulations. This is not for data integrity; it is a resilience control that prevents runaway parallelism under load.

## Stateless multi-instance readiness

The app is designed to scale horizontally because it does not keep shopping carts or authentication sessions in local memory:

- authentication uses JWT
- cart and order state live in the database
- catalog cache uses Redis instead of in-process memory

That means a load balancer can distribute requests across multiple instances without sticky sessions.

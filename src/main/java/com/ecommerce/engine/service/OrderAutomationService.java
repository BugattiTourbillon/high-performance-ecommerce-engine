package com.ecommerce.engine.service;

import com.ecommerce.engine.entity.CustomerOrder;
import com.ecommerce.engine.entity.OrderItem;
import com.ecommerce.engine.event.OrderPlacedEvent;
import com.ecommerce.engine.exception.ResourceNotFoundException;
import com.ecommerce.engine.repository.CustomerOrderRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAutomationService {

    private final CustomerOrderRepository customerOrderRepository;

    @Async("commerceTaskExecutor")
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        CustomerOrder order = customerOrderRepository.findById(event.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order not found for async automation: " + event.orderId()));

        Path invoicePath = generateInvoicePdf(order);
        log.info("Simulated email notification for order {} to {} with invoice {}",
            order.getId(), event.customerEmail(), invoicePath.toAbsolutePath());
    }

    private Path generateInvoicePdf(CustomerOrder order) {
        try {
            Path invoiceDirectory = Path.of("generated", "invoices");
            Files.createDirectories(invoiceDirectory);
            Path invoiceFile = invoiceDirectory.resolve("order-" + order.getId() + ".pdf");

            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
                    contentStream.newLineAtOffset(50, 750);
                    contentStream.showText("Invoice for Order #" + order.getId());
                    contentStream.setFont(PDType1Font.HELVETICA, 11);
                    contentStream.newLineAtOffset(0, -25);
                    contentStream.showText("Customer: " + order.getCustomerEmail());
                    contentStream.newLineAtOffset(0, -18);
                    contentStream.showText("Status: " + order.getStatus().name());
                    contentStream.newLineAtOffset(0, -18);
                    contentStream.showText("Total: " + order.getTotalAmount());
                    for (OrderItem item : order.getItems()) {
                        contentStream.newLineAtOffset(0, -18);
                        contentStream.showText(item.getQuantity() + " x " + item.getProductNameSnapshot() + " @ " + item.getUnitPriceSnapshot());
                    }
                    contentStream.endText();
                }

                document.save(invoiceFile.toFile());
            }
            return invoiceFile;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate invoice PDF", ex);
        }
    }
}

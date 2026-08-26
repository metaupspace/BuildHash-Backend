package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceSnapshotBuilderImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private HsnGstRateRepository hsnGstRateRepository;

    private InvoiceSnapshotBuilderImpl builder;

    @BeforeEach
    void setUp() {
        builder = new InvoiceSnapshotBuilderImpl(orderRepository, userRepository, addressRepository, productRepository, hsnGstRateRepository);
    }

    @Test
    void build_completeOrder_assemblesSnapshotWithAccurateTotalsAndHsnRates() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        OrderLineItem item = new OrderLineItem(
                UUID.randomUUID(),
                productId,
                2,
                new BigDecimal("500.00"),
                new BigDecimal("180.00"),
                new BigDecimal("1180.00")
        );

        Order order = new Order(
                orderId,
                userId,
                addressId,
                UUID.randomUUID(),
                LocalDate.now(),
                new BigDecimal("1180.00"),
                OrderStatus.CONFIRMED,
                UUID.randomUUID(),
                Instant.now(),
                null,
                null,
                List.of(item)
        );

        User user = new User();
        user.setId(userId);
        user.setPhone("+919999999999");

        Address address = new Address(
                addressId,
                userId,
                "HOME",
                "123 Street",
                "Apt 4B",
                "Bengaluru",
                "Karnataka",
                "560001",
                12.97,
                77.59,
                true
        );

        Product product = new Product();
        product.setId(productId);
        product.setName("Steel Rods");
        product.setHsnCode("7214");

        HsnGstRate hsnGstRate = new HsnGstRate("7214", "Steel", new BigDecimal("18.00"), "Steel", Instant.now(), Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.findById(addressId)).thenReturn(Optional.of(address));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(hsnGstRateRepository.findByHsnCode("7214")).thenReturn(Optional.of(hsnGstRate));

        OrderInvoiceSnapshot snapshot = builder.build(orderId);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.orderId()).isEqualTo(orderId);
        assertThat(snapshot.customerPhone()).isEqualTo("+919999999999");
        assertThat(snapshot.deliveryAddress()).contains("123 Street", "Bengaluru", "560001");
        assertThat(snapshot.totalTax()).isEqualByComparingTo("180.00");
        assertThat(snapshot.subTotal()).isEqualByComparingTo("1000.00");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("1180.00");

        assertThat(snapshot.lineItems()).hasSize(1);
        OrderInvoiceSnapshot.InvoiceLineItemSnapshot lineItem = snapshot.lineItems().get(0);
        assertThat(lineItem.productName()).isEqualTo("Steel Rods");
        assertThat(lineItem.hsnCode()).isEqualTo("7214");
        assertThat(lineItem.taxRate()).isEqualByComparingTo("18.00");
        assertThat(lineItem.lineTotal()).isEqualByComparingTo("1180.00");
    }

    @Test
    void build_orderNotFound_throwsNotFoundException() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> builder.build(orderId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Order not found");
    }
}

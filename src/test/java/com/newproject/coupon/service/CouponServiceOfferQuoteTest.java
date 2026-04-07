package com.newproject.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.newproject.coupon.domain.Coupon;
import com.newproject.coupon.dto.PriceQuoteRequest;
import com.newproject.coupon.dto.PriceQuoteResponse;
import com.newproject.coupon.events.EventPublisher;
import com.newproject.coupon.repository.CouponRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CouponServiceOfferQuoteTest {

    @Test
    void quoteAppliesExplicitCouponAndStackableAutomaticShippingOffer() {
        CouponRepository repository = mock(CouponRepository.class);
        CouponService service = new CouponService(repository, mock(EventPublisher.class), mock(CouponTranslationService.class));

        Coupon vip10 = coupon("VIP10", "VIP 10", "PERCENT", "ORDER", false, true, "VIP", 5, new BigDecimal("10"));
        Coupon shipfree = coupon("SHIPFREE", "Free shipping", "PERCENT", "SHIPPING", true, true, "VIP", 3, new BigDecimal("100"));

        when(repository.findByCodeIgnoreCase("VIP10")).thenReturn(Optional.of(vip10));
        when(repository.findAll()).thenReturn(List.of(vip10, shipfree));

        PriceQuoteRequest request = new PriceQuoteRequest();
        request.setSubtotal(new BigDecimal("100.00"));
        request.setShipping(new BigDecimal("8.00"));
        request.setCouponCode("VIP10");
        request.setCustomerGroupCode("VIP");

        PriceQuoteResponse response = service.quote(request, "it");

        assertThat(response.getCouponDiscount()).isEqualByComparingTo("10.0000");
        assertThat(response.getShippingDiscount()).isEqualByComparingTo("8.0000");
        assertThat(response.getDiscount()).isEqualByComparingTo("18.0000");
        assertThat(response.getTotal()).isEqualByComparingTo("90.0000");
        assertThat(response.getAppliedOffers()).hasSize(2);
    }

    private Coupon coupon(String code, String name, String discountType, String offerScope, boolean autoApply, boolean stackable, String group, int priority, BigDecimal value) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setName(name);
        coupon.setDiscountType(discountType);
        coupon.setOfferScope(offerScope);
        coupon.setAutoApply(autoApply);
        coupon.setStackable(stackable);
        coupon.setCustomerGroupCode(group);
        coupon.setPriority(priority);
        coupon.setValue(value);
        coupon.setMinTotal(BigDecimal.ZERO);
        coupon.setCurrency("EUR");
        coupon.setActive(true);
        coupon.setUsedCount(0);
        coupon.setUpdatedAt(OffsetDateTime.parse("2026-04-07T10:15:30Z"));
        return coupon;
    }
}

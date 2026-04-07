package com.newproject.coupon.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class PriceQuoteResponse {
    private BigDecimal subtotal;
    private BigDecimal shipping;
    private BigDecimal discount;
    private BigDecimal couponDiscount;
    private BigDecimal automaticDiscount;
    private BigDecimal shippingDiscount;
    private BigDecimal total;
    private String appliedCoupon;
    private String message;
    private List<AppliedOfferResponse> appliedOffers = new ArrayList<>();

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getShipping() { return shipping; }
    public void setShipping(BigDecimal shipping) { this.shipping = shipping; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
    public BigDecimal getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(BigDecimal couponDiscount) { this.couponDiscount = couponDiscount; }
    public BigDecimal getAutomaticDiscount() { return automaticDiscount; }
    public void setAutomaticDiscount(BigDecimal automaticDiscount) { this.automaticDiscount = automaticDiscount; }
    public BigDecimal getShippingDiscount() { return shippingDiscount; }
    public void setShippingDiscount(BigDecimal shippingDiscount) { this.shippingDiscount = shippingDiscount; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getAppliedCoupon() { return appliedCoupon; }
    public void setAppliedCoupon(String appliedCoupon) { this.appliedCoupon = appliedCoupon; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<AppliedOfferResponse> getAppliedOffers() { return appliedOffers; }
    public void setAppliedOffers(List<AppliedOfferResponse> appliedOffers) { this.appliedOffers = appliedOffers; }
}

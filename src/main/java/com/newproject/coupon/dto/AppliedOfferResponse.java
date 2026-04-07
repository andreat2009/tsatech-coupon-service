package com.newproject.coupon.dto;

import java.math.BigDecimal;

public class AppliedOfferResponse {
    private String code;
    private String name;
    private String offerScope;
    private boolean automatic;
    private BigDecimal discount;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOfferScope() { return offerScope; }
    public void setOfferScope(String offerScope) { this.offerScope = offerScope; }
    public boolean isAutomatic() { return automatic; }
    public void setAutomatic(boolean automatic) { this.automatic = automatic; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }
}

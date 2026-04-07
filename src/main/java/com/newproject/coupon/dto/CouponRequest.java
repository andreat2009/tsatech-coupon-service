package com.newproject.coupon.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class CouponRequest {
    @NotBlank
    private String code;

    private String name;

    @NotBlank
    @Pattern(regexp = "^(FIXED|PERCENT)$", message = "discountType must be FIXED or PERCENT")
    private String discountType;

    @Pattern(regexp = "^(ORDER|SHIPPING)$", message = "offerScope must be ORDER or SHIPPING")
    private String offerScope;

    private Boolean autoApply;

    private Boolean stackable;

    private String customerGroupCode;

    private Integer priority;

    @NotNull
    @DecimalMin(value = "0.0001", inclusive = true)
    private BigDecimal value;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal minTotal;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal maxDiscount;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be 3-letter ISO code")
    private String currency;

    @NotNull
    private Boolean active;

    private OffsetDateTime dateStart;
    private OffsetDateTime dateEnd;
    private Integer usageLimit;
    private Map<String, LocalizedContent> translations = new LinkedHashMap<>();

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public String getOfferScope() { return offerScope; }
    public void setOfferScope(String offerScope) { this.offerScope = offerScope; }
    public Boolean getAutoApply() { return autoApply; }
    public void setAutoApply(Boolean autoApply) { this.autoApply = autoApply; }
    public Boolean getStackable() { return stackable; }
    public void setStackable(Boolean stackable) { this.stackable = stackable; }
    public String getCustomerGroupCode() { return customerGroupCode; }
    public void setCustomerGroupCode(String customerGroupCode) { this.customerGroupCode = customerGroupCode; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getMinTotal() { return minTotal; }
    public void setMinTotal(BigDecimal minTotal) { this.minTotal = minTotal; }
    public BigDecimal getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(BigDecimal maxDiscount) { this.maxDiscount = maxDiscount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public OffsetDateTime getDateStart() { return dateStart; }
    public void setDateStart(OffsetDateTime dateStart) { this.dateStart = dateStart; }
    public OffsetDateTime getDateEnd() { return dateEnd; }
    public void setDateEnd(OffsetDateTime dateEnd) { this.dateEnd = dateEnd; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Map<String, LocalizedContent> getTranslations() { return translations; }
    public void setTranslations(Map<String, LocalizedContent> translations) { this.translations = translations; }
}

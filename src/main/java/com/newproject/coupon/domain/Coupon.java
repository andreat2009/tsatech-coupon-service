package com.newproject.coupon.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "coupon_service_coupon")
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false, unique = true)
    private String code;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(name = "discount_type", length = 16, nullable = false)
    private String discountType;

    @Column(name = "offer_scope", length = 16, nullable = false)
    private String offerScope;

    @Column(name = "auto_apply", nullable = false)
    private Boolean autoApply;

    @Column(name = "stackable", nullable = false)
    private Boolean stackable;

    @Column(name = "customer_group_code", length = 64)
    private String customerGroupCode;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal value;

    @Column(name = "min_total", nullable = false, precision = 15, scale = 4)
    private BigDecimal minTotal;

    @Column(name = "max_discount", precision = 15, scale = 4)
    private BigDecimal maxDiscount;

    @Column(length = 3, nullable = false)
    private String currency;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "date_start")
    private OffsetDateTime dateStart;

    @Column(name = "date_end")
    private OffsetDateTime dateEnd;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CouponTranslation> translations = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<CouponTranslation> getTranslations() { return translations; }
    public void setTranslations(List<CouponTranslation> translations) { this.translations = translations; }
}

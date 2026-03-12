package com.newproject.coupon.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class CouponAutoTranslateRequest {
    private String sourceLanguage;
    private Boolean overwriteExisting;
    private Map<String, LocalizedContent> translations = new LinkedHashMap<>();

    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public Boolean getOverwriteExisting() {
        return overwriteExisting;
    }

    public void setOverwriteExisting(Boolean overwriteExisting) {
        this.overwriteExisting = overwriteExisting;
    }

    public Map<String, LocalizedContent> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, LocalizedContent> translations) {
        this.translations = translations;
    }
}

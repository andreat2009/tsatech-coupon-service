package com.newproject.coupon.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CouponAutoTranslateResponse {
    private Map<String, LocalizedContent> translations = new LinkedHashMap<>();
    private List<String> translatedLanguages = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public Map<String, LocalizedContent> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, LocalizedContent> translations) {
        this.translations = translations;
    }

    public List<String> getTranslatedLanguages() {
        return translatedLanguages;
    }

    public void setTranslatedLanguages(List<String> translatedLanguages) {
        this.translatedLanguages = translatedLanguages;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}

package com.newproject.coupon.service.translation;

import com.newproject.coupon.dto.LocalizedContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TranslationResult {
    private Map<String, LocalizedContent> translations = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    public Map<String, LocalizedContent> getTranslations() {
        return translations;
    }

    public void setTranslations(Map<String, LocalizedContent> translations) {
        this.translations = translations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}

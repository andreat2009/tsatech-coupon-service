package com.newproject.coupon.service;

import com.newproject.coupon.config.CouponTranslationProperties;
import com.newproject.coupon.dto.LocalizedContent;
import com.newproject.coupon.service.translation.NoopTranslationProvider;
import com.newproject.coupon.service.translation.OpenAiTranslationProvider;
import com.newproject.coupon.service.translation.TranslationResult;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CouponTranslationService {
    private final CouponTranslationProperties properties;
    private final OpenAiTranslationProvider openAiTranslationProvider;
    private final NoopTranslationProvider noopTranslationProvider;

    public CouponTranslationService(
        CouponTranslationProperties properties,
        OpenAiTranslationProvider openAiTranslationProvider,
        NoopTranslationProvider noopTranslationProvider
    ) {
        this.properties = properties;
        this.openAiTranslationProvider = openAiTranslationProvider;
        this.noopTranslationProvider = noopTranslationProvider;
    }

    public TranslationResult translateCouponContent(String sourceLanguage, LocalizedContent sourceContent, Set<String> targetLanguages) {
        if (!properties.isEnabled()) {
            TranslationResult result = noopTranslationProvider.translateCouponContent(sourceLanguage, sourceContent, targetLanguages);
            result.getWarnings().add("Translation disabled");
            return result;
        }

        String provider = properties.getProvider() != null ? properties.getProvider().trim().toLowerCase() : "";
        if ("openai".equals(provider)) {
            return openAiTranslationProvider.translateCouponContent(sourceLanguage, sourceContent, targetLanguages);
        }

        TranslationResult result = noopTranslationProvider.translateCouponContent(sourceLanguage, sourceContent, targetLanguages);
        result.getWarnings().add("Unsupported translation provider: " + provider);
        return result;
    }
}

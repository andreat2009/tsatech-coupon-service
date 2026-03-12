package com.newproject.coupon.service.translation;

import com.newproject.coupon.dto.LocalizedContent;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NoopTranslationProvider implements TranslationProvider {
    @Override
    public TranslationResult translateCouponContent(String sourceLanguage, LocalizedContent sourceContent, Set<String> targetLanguages) {
        return new TranslationResult();
    }
}

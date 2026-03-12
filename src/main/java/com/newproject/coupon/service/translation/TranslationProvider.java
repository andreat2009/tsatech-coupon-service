package com.newproject.coupon.service.translation;

import com.newproject.coupon.dto.LocalizedContent;
import java.util.Set;

public interface TranslationProvider {
    TranslationResult translateCouponContent(String sourceLanguage, LocalizedContent sourceContent, Set<String> targetLanguages);
}

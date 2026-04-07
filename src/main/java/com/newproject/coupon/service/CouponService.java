package com.newproject.coupon.service;

import com.newproject.coupon.domain.Coupon;
import com.newproject.coupon.domain.CouponTranslation;
import com.newproject.coupon.dto.AppliedOfferResponse;
import com.newproject.coupon.dto.CouponAutoTranslateRequest;
import com.newproject.coupon.dto.CouponAutoTranslateResponse;
import com.newproject.coupon.dto.CouponRequest;
import com.newproject.coupon.dto.CouponResponse;
import com.newproject.coupon.dto.LocalizedContent;
import com.newproject.coupon.dto.PriceQuoteRequest;
import com.newproject.coupon.dto.PriceQuoteResponse;
import com.newproject.coupon.events.EventPublisher;
import com.newproject.coupon.exception.BadRequestException;
import com.newproject.coupon.exception.NotFoundException;
import com.newproject.coupon.repository.CouponRepository;
import com.newproject.coupon.service.translation.TranslationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private static final String DEFAULT_SCOPE = "ORDER";

    private final CouponRepository couponRepository;
    private final EventPublisher eventPublisher;
    private final CouponTranslationService couponTranslationService;

    public CouponService(
        CouponRepository couponRepository,
        EventPublisher eventPublisher,
        CouponTranslationService couponTranslationService
    ) {
        this.couponRepository = couponRepository;
        this.eventPublisher = eventPublisher;
        this.couponTranslationService = couponTranslationService;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> list(String language) {
        String resolvedLanguage = LanguageSupport.normalizeLanguage(language);
        if (resolvedLanguage == null) {
            resolvedLanguage = LanguageSupport.DEFAULT_LANGUAGE;
        }

        String finalResolvedLanguage = resolvedLanguage;
        return couponRepository.findAll().stream()
            .sorted(Comparator.comparing(Coupon::getPriority, Comparator.nullsLast(Integer::compareTo)).reversed())
            .map(coupon -> toResponse(coupon, finalResolvedLanguage))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CouponResponse get(Long id, String language) {
        Coupon coupon = couponRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Coupon not found"));
        return toResponse(coupon, language);
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {
        couponRepository.findByCodeIgnoreCase(request.getCode())
            .ifPresent(existing -> {
                throw new BadRequestException("Coupon code already exists");
            });

        Coupon coupon = new Coupon();
        applyRequest(coupon, request);
        coupon.setUsedCount(0);
        coupon.setUpdatedAt(OffsetDateTime.now());

        Coupon saved = couponRepository.save(coupon);
        CouponResponse response = toResponse(saved, LanguageSupport.DEFAULT_LANGUAGE);
        eventPublisher.publish("COUPON_CREATED", "coupon", saved.getId().toString(), response);
        return response;
    }

    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Coupon not found"));

        couponRepository.findByCodeIgnoreCase(request.getCode())
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BadRequestException("Coupon code already exists");
            });

        applyRequest(coupon, request);
        coupon.setUpdatedAt(OffsetDateTime.now());

        Coupon saved = couponRepository.save(coupon);
        CouponResponse response = toResponse(saved, LanguageSupport.DEFAULT_LANGUAGE);
        eventPublisher.publish("COUPON_UPDATED", "coupon", saved.getId().toString(), response);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Coupon coupon = couponRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Coupon not found"));

        couponRepository.delete(coupon);
        eventPublisher.publish("COUPON_DELETED", "coupon", id.toString(), null);
    }

    @Transactional(readOnly = true)
    public CouponAutoTranslateResponse autoTranslate(CouponAutoTranslateRequest request) {
        Map<String, LocalizedContent> normalized = normalizeTranslationPayload(
            request != null ? request.getTranslations() : null
        );

        String sourceLanguage = LanguageSupport.normalizeLanguage(request != null ? request.getSourceLanguage() : null);
        if (sourceLanguage == null) {
            sourceLanguage = LanguageSupport.DEFAULT_LANGUAGE;
        }

        LocalizedContent sourceContent = normalized.get(sourceLanguage);
        if (sourceContent == null) {
            sourceContent = new LocalizedContent();
            normalized.put(sourceLanguage, sourceContent);
        }

        if (trimToNull(sourceContent.getName()) == null) {
            throw new BadRequestException("Source language coupon name is required");
        }

        boolean overwrite = request != null && Boolean.TRUE.equals(request.getOverwriteExisting());
        Set<String> targets = new LinkedHashSet<>();
        for (String language : LanguageSupport.SUPPORTED_LANGUAGES) {
            if (language.equals(sourceLanguage)) {
                continue;
            }

            LocalizedContent current = normalized.get(language);
            if (overwrite || isBlank(current != null ? current.getName() : null)) {
                targets.add(language);
            }
        }

        TranslationResult translationResult = couponTranslationService.translateCouponContent(sourceLanguage, sourceContent, targets);
        Set<String> translatedLanguages = new LinkedHashSet<>();

        if (translationResult.getTranslations() != null) {
            for (Map.Entry<String, LocalizedContent> entry : translationResult.getTranslations().entrySet()) {
                String language = LanguageSupport.normalizeLanguage(entry.getKey());
                if (language == null || language.equals(sourceLanguage)) {
                    continue;
                }

                LocalizedContent translated = entry.getValue();
                if (translated == null) {
                    continue;
                }

                LocalizedContent current = normalized.get(language);
                if (current == null) {
                    current = new LocalizedContent();
                    normalized.put(language, current);
                }

                String translatedName = trimToNull(translated.getName());
                if (translatedName != null && (overwrite || isBlank(current.getName()))) {
                    current.setName(translatedName);
                    translatedLanguages.add(language);
                }
            }
        }

        CouponAutoTranslateResponse response = new CouponAutoTranslateResponse();
        response.setTranslations(normalized);
        response.setTranslatedLanguages(new ArrayList<>(translatedLanguages));
        response.setWarnings(translationResult.getWarnings() != null
            ? new ArrayList<>(translationResult.getWarnings())
            : new ArrayList<>());
        return response;
    }

    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(PriceQuoteRequest request, String language) {
        String resolvedLanguage = LanguageSupport.normalizeLanguage(language);
        if (resolvedLanguage == null) {
            resolvedLanguage = LanguageSupport.DEFAULT_LANGUAGE;
        }

        BigDecimal subtotal = notNull(request.getSubtotal());
        BigDecimal shipping = notNull(request.getShipping());
        String customerGroupCode = normalizeCustomerGroupCode(request.getCustomerGroupCode());

        PriceQuoteResponse response = new PriceQuoteResponse();
        response.setSubtotal(subtotal);
        response.setShipping(shipping);
        response.setCouponDiscount(BigDecimal.ZERO);
        response.setAutomaticDiscount(BigDecimal.ZERO);
        response.setShippingDiscount(BigDecimal.ZERO);

        List<AppliedOfferResponse> appliedOffers = new ArrayList<>();
        BigDecimal orderDiscount = BigDecimal.ZERO;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        String message = null;

        Coupon explicitCoupon = null;
        String couponCode = trimToNull(request.getCouponCode());
        if (couponCode != null) {
            explicitCoupon = couponRepository.findByCodeIgnoreCase(couponCode).orElse(null);
            if (explicitCoupon == null) {
                message = localizedMessage("coupon.invalid", resolvedLanguage);
            } else if (!isCouponUsable(explicitCoupon, subtotal, customerGroupCode)) {
                message = localizedMessage("coupon.not_applicable", resolvedLanguage);
                explicitCoupon = null;
            } else {
                BigDecimal explicitDiscount = calculateDiscount(explicitCoupon, subtotal.subtract(orderDiscount), shipping.subtract(shippingDiscount));
                if (isShippingScope(explicitCoupon)) {
                    shippingDiscount = shippingDiscount.add(explicitDiscount);
                } else {
                    orderDiscount = orderDiscount.add(explicitDiscount);
                    response.setCouponDiscount(explicitDiscount);
                }
                response.setAppliedCoupon(explicitCoupon.getCode());
                appliedOffers.add(toAppliedOffer(explicitCoupon, explicitDiscount, false));
                message = localizedMessage("coupon.applied", resolvedLanguage);
            }
        }

        final Coupon finalExplicitCoupon = explicitCoupon;
        boolean allowAutomaticOffers = finalExplicitCoupon == null || Boolean.TRUE.equals(finalExplicitCoupon.getStackable());
        if (allowAutomaticOffers) {
            boolean chainStackable = finalExplicitCoupon == null || Boolean.TRUE.equals(finalExplicitCoupon.getStackable());
            for (Coupon autoOffer : couponRepository.findAll().stream()
                .filter(coupon -> Boolean.TRUE.equals(coupon.getAutoApply()))
                .filter(coupon -> !isSameCoupon(coupon, finalExplicitCoupon))
                .filter(coupon -> isCouponUsable(coupon, subtotal, customerGroupCode))
                .sorted(Comparator.comparing(Coupon::getPriority, Comparator.nullsLast(Integer::compareTo)).reversed()
                    .thenComparing(Coupon::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList()) {

                if (!chainStackable) {
                    break;
                }

                BigDecimal scopedBase = isShippingScope(autoOffer)
                    ? shipping.subtract(shippingDiscount)
                    : subtotal.subtract(orderDiscount);
                BigDecimal discount = calculateDiscount(autoOffer, subtotal.subtract(orderDiscount), shipping.subtract(shippingDiscount));
                if (scopedBase.compareTo(BigDecimal.ZERO) <= 0 || discount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                if (isShippingScope(autoOffer)) {
                    shippingDiscount = shippingDiscount.add(discount);
                } else {
                    orderDiscount = orderDiscount.add(discount);
                    response.setAutomaticDiscount(response.getAutomaticDiscount().add(discount));
                }
                appliedOffers.add(toAppliedOffer(autoOffer, discount, true));
                chainStackable = Boolean.TRUE.equals(autoOffer.getStackable());
            }
        }

        BigDecimal totalDiscount = orderDiscount.add(shippingDiscount);
        BigDecimal total = subtotal.add(shipping).subtract(totalDiscount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        response.setShippingDiscount(scale(shippingDiscount));
        response.setDiscount(scale(totalDiscount));
        response.setTotal(scale(total));
        response.setAppliedOffers(appliedOffers);
        if (message == null && !appliedOffers.isEmpty()) {
            message = localizedMessage("coupon.auto_applied", resolvedLanguage);
        }
        response.setMessage(message);
        return response;
    }

    private boolean isCouponUsable(Coupon coupon, BigDecimal subtotal, String customerGroupCode) {
        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (coupon.getDateStart() != null && coupon.getDateStart().isAfter(now)) {
            return false;
        }
        if (coupon.getDateEnd() != null && coupon.getDateEnd().isBefore(now)) {
            return false;
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return false;
        }
        String requiredGroupCode = normalizeCustomerGroupCode(coupon.getCustomerGroupCode());
        if (requiredGroupCode != null && !requiredGroupCode.equals(customerGroupCode)) {
            return false;
        }
        return subtotal.compareTo(notNull(coupon.getMinTotal())) >= 0;
    }

    private boolean isSameCoupon(Coupon left, Coupon right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return trimToNull(left.getCode()) != null && trimToNull(left.getCode()).equalsIgnoreCase(trimToNull(right.getCode()));
    }

    private BigDecimal calculateDiscount(Coupon coupon, BigDecimal subtotalBase, BigDecimal shippingBase) {
        BigDecimal scopedBase = isShippingScope(coupon) ? shippingBase : subtotalBase;
        if (scopedBase == null || scopedBase.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        String type = coupon.getDiscountType() != null ? coupon.getDiscountType().toUpperCase(Locale.ROOT) : "FIXED";
        BigDecimal discount;

        if ("PERCENT".equals(type)) {
            discount = scopedBase.multiply(coupon.getValue()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getValue();
        }

        if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
            discount = coupon.getMaxDiscount();
        }

        if (discount.compareTo(scopedBase) > 0) {
            discount = scopedBase;
        }

        return scale(discount);
    }

    private AppliedOfferResponse toAppliedOffer(Coupon coupon, BigDecimal discount, boolean automatic) {
        AppliedOfferResponse response = new AppliedOfferResponse();
        response.setCode(coupon.getCode());
        response.setName(coupon.getName());
        response.setOfferScope(normalizeOfferScope(coupon.getOfferScope()));
        response.setAutomatic(automatic);
        response.setDiscount(scale(discount));
        return response;
    }

    private boolean isShippingScope(Coupon coupon) {
        return "SHIPPING".equals(normalizeOfferScope(coupon.getOfferScope()));
    }

    private void applyRequest(Coupon coupon, CouponRequest request) {
        Map<String, LocalizedContent> normalizedTranslations = normalizeTranslations(
            request.getTranslations(),
            request.getName(),
            coupon.getName()
        );

        LocalizedContent defaultContent = normalizedTranslations.get(LanguageSupport.DEFAULT_LANGUAGE);

        coupon.setCode(request.getCode() != null ? request.getCode().trim().toUpperCase(Locale.ROOT) : null);
        coupon.setName(defaultContent.getName());
        syncTranslations(coupon, normalizedTranslations);
        coupon.setDiscountType(request.getDiscountType() != null ? request.getDiscountType().trim().toUpperCase(Locale.ROOT) : null);
        coupon.setOfferScope(normalizeOfferScope(request.getOfferScope()));
        coupon.setAutoApply(Boolean.TRUE.equals(request.getAutoApply()));
        coupon.setStackable(request.getStackable() == null ? Boolean.TRUE : request.getStackable());
        coupon.setCustomerGroupCode(normalizeCustomerGroupCode(request.getCustomerGroupCode()));
        coupon.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        coupon.setValue(scale(request.getValue()));
        coupon.setMinTotal(scale(request.getMinTotal()));
        coupon.setMaxDiscount(request.getMaxDiscount() != null ? scale(request.getMaxDiscount()) : null);
        coupon.setCurrency(request.getCurrency() != null ? request.getCurrency().trim().toUpperCase(Locale.ROOT) : null);
        coupon.setActive(request.getActive());
        coupon.setDateStart(request.getDateStart());
        coupon.setDateEnd(request.getDateEnd());
        coupon.setUsageLimit(request.getUsageLimit());
        if (coupon.getUsedCount() == null) {
            coupon.setUsedCount(0);
        }
    }

    private void syncTranslations(Coupon coupon, Map<String, LocalizedContent> localizedContents) {
        Map<String, CouponTranslation> existingByLanguage = coupon.getTranslations().stream()
            .collect(Collectors.toMap(
                translation -> translation.getLanguageCode().toLowerCase(Locale.ROOT),
                translation -> translation,
                (first, ignored) -> first
            ));

        for (String language : LanguageSupport.SUPPORTED_LANGUAGES) {
            LocalizedContent localizedContent = localizedContents.get(language);
            CouponTranslation translation = existingByLanguage.get(language);
            if (translation == null) {
                translation = new CouponTranslation();
                translation.setCoupon(coupon);
                translation.setLanguageCode(language);
                coupon.getTranslations().add(translation);
                existingByLanguage.put(language, translation);
            }
            translation.setName(localizedContent.getName());
        }

        coupon.getTranslations().removeIf(translation ->
            !LanguageSupport.SUPPORTED_LANGUAGES.contains(translation.getLanguageCode().toLowerCase(Locale.ROOT)));
    }

    private CouponResponse toResponse(Coupon coupon, String language) {
        String resolvedLanguage = LanguageSupport.normalizeLanguage(language);
        if (resolvedLanguage == null) {
            resolvedLanguage = LanguageSupport.DEFAULT_LANGUAGE;
        }

        Map<String, LocalizedContent> translations = toTranslationMap(coupon.getTranslations(), coupon.getName());
        LocalizedContent localized = translations.getOrDefault(resolvedLanguage, translations.get(LanguageSupport.DEFAULT_LANGUAGE));

        CouponResponse response = new CouponResponse();
        response.setId(coupon.getId());
        response.setCode(coupon.getCode());
        response.setName(localized != null ? localized.getName() : coupon.getName());
        response.setDiscountType(coupon.getDiscountType());
        response.setOfferScope(normalizeOfferScope(coupon.getOfferScope()));
        response.setAutoApply(coupon.getAutoApply());
        response.setStackable(coupon.getStackable());
        response.setCustomerGroupCode(normalizeCustomerGroupCode(coupon.getCustomerGroupCode()));
        response.setPriority(coupon.getPriority());
        response.setValue(coupon.getValue());
        response.setMinTotal(coupon.getMinTotal());
        response.setMaxDiscount(coupon.getMaxDiscount());
        response.setCurrency(coupon.getCurrency());
        response.setActive(coupon.getActive());
        response.setDateStart(coupon.getDateStart());
        response.setDateEnd(coupon.getDateEnd());
        response.setUsageLimit(coupon.getUsageLimit());
        response.setUsedCount(coupon.getUsedCount());
        response.setUpdatedAt(coupon.getUpdatedAt());
        response.setTranslations(translations);
        return response;
    }

    private Map<String, LocalizedContent> toTranslationMap(List<CouponTranslation> translations, String fallbackName) {
        Map<String, LocalizedContent> map = new LinkedHashMap<>();
        Map<String, CouponTranslation> byLanguage = translations.stream()
            .collect(Collectors.toMap(
                translation -> translation.getLanguageCode().toLowerCase(Locale.ROOT),
                translation -> translation,
                (first, ignored) -> first
            ));

        for (String language : LanguageSupport.SUPPORTED_LANGUAGES) {
            CouponTranslation translation = byLanguage.get(language);
            LocalizedContent content = new LocalizedContent();
            content.setName(firstNonBlank(
                translation != null ? translation.getName() : null,
                language.equals(LanguageSupport.DEFAULT_LANGUAGE) ? fallbackName : null,
                fallbackName
            ));
            map.put(language, content);
        }

        return map;
    }

    private Map<String, LocalizedContent> normalizeTranslations(
        Map<String, LocalizedContent> requested,
        String fallbackName,
        String existingName
    ) {
        Map<String, LocalizedContent> normalized = new LinkedHashMap<>();

        String defaultName = firstNonBlank(
            extractName(requested, LanguageSupport.DEFAULT_LANGUAGE),
            fallbackName,
            existingName
        );

        if (defaultName == null || defaultName.isBlank()) {
            throw new BadRequestException("Coupon name is required");
        }

        for (String language : LanguageSupport.SUPPORTED_LANGUAGES) {
            LocalizedContent content = new LocalizedContent();
            String name = firstNonBlank(
                extractName(requested, language),
                language.equals(LanguageSupport.DEFAULT_LANGUAGE) ? fallbackName : null,
                defaultName
            );
            content.setName(name != null ? name : defaultName);
            normalized.put(language, content);
        }

        return normalized;
    }

    private Map<String, LocalizedContent> normalizeTranslationPayload(Map<String, LocalizedContent> requested) {
        Map<String, LocalizedContent> normalized = new LinkedHashMap<>();
        for (String language : LanguageSupport.SUPPORTED_LANGUAGES) {
            LocalizedContent source = requested != null ? requested.get(language) : null;
            LocalizedContent content = new LocalizedContent();
            content.setName(trimToNull(source != null ? source.getName() : null));
            normalized.put(language, content);
        }
        return normalized;
    }

    private String extractName(Map<String, LocalizedContent> requested, String language) {
        if (requested == null) {
            return null;
        }
        LocalizedContent content = requested.get(language);
        if (content == null) {
            return null;
        }
        return trimToNull(content.getName());
    }

    private String localizedMessage(String key, String language) {
        return switch (language) {
            case "en" -> switch (key) {
                case "coupon.invalid" -> "Invalid coupon";
                case "coupon.not_applicable" -> "Coupon not applicable";
                case "coupon.applied" -> "Offer applied";
                case "coupon.auto_applied" -> "Automatic offer applied";
                default -> null;
            };
            case "fr" -> switch (key) {
                case "coupon.invalid" -> "Coupon invalide";
                case "coupon.not_applicable" -> "Coupon non applicable";
                case "coupon.applied" -> "Offre appliquee";
                case "coupon.auto_applied" -> "Offre automatique appliquee";
                default -> null;
            };
            case "de" -> switch (key) {
                case "coupon.invalid" -> "Ungultiger Gutschein";
                case "coupon.not_applicable" -> "Gutschein nicht anwendbar";
                case "coupon.applied" -> "Angebot angewendet";
                case "coupon.auto_applied" -> "Automatisches Angebot angewendet";
                default -> null;
            };
            case "es" -> switch (key) {
                case "coupon.invalid" -> "Cupon invalido";
                case "coupon.not_applicable" -> "Cupon no aplicable";
                case "coupon.applied" -> "Oferta aplicada";
                case "coupon.auto_applied" -> "Oferta automatica aplicada";
                default -> null;
            };
            default -> switch (key) {
                case "coupon.invalid" -> "Coupon non valido";
                case "coupon.not_applicable" -> "Coupon non applicabile";
                case "coupon.applied" -> "Offerta applicata";
                case "coupon.auto_applied" -> "Offerta automatica applicata";
                default -> null;
            };
        };
    }

    private BigDecimal notNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal scale(BigDecimal value) {
        return value != null ? value.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeOfferScope(String offerScope) {
        if (offerScope == null || offerScope.isBlank()) {
            return DEFAULT_SCOPE;
        }
        return offerScope.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCustomerGroupCode(String customerGroupCode) {
        if (customerGroupCode == null || customerGroupCode.isBlank()) {
            return null;
        }
        return customerGroupCode.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }
}

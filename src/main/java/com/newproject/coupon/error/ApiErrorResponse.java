package com.newproject.coupon.error;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    String reference
) {
}

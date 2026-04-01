package com.newproject.coupon.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handle(Exception exception, HttpServletRequest request) {
        HttpStatus status = resolveStatus(exception);
        String reference = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        String message = resolveMessage(exception, status);
        if (status.is5xxServerError()) {
            logger.error("Unhandled API error [{}] on {}", reference, path, exception);
        } else {
            logger.warn("API request error [{}] on {}: {}", reference, path, exception.getMessage());
        }
        ApiErrorResponse body = new ApiErrorResponse(
            OffsetDateTime.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            reference
        );
        return ResponseEntity.status(status).body(body);
    }

    private HttpStatus resolveStatus(Exception exception) {
        String simpleName = exception.getClass().getSimpleName();
        if (exception instanceof MaxUploadSizeExceededException) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (exception instanceof MultipartException
            || exception instanceof BindException
            || exception instanceof MethodArgumentNotValidException
            || exception instanceof MissingServletRequestParameterException
            || exception instanceof MethodArgumentTypeMismatchException
            || "BadRequestException".equals(simpleName)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof NoResourceFoundException || "NotFoundException".equals(simpleName)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("AccessDeniedException".equals(simpleName)) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getBody().getStatus());
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String resolveMessage(Exception exception, HttpStatus status) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return "Unexpected server error. Please retry later.";
        }
        if (status == HttpStatus.PAYLOAD_TOO_LARGE) {
            return "The submitted payload exceeds the accepted size.";
        }
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return status.getReasonPhrase();
    }
}

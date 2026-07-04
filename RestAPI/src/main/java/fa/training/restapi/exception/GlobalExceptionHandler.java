package fa.training.restapi.exception;

import fa.training.restapi.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Exception: ", exception);
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                .build());
    }

    @ExceptionHandler(value = PropertyReferenceException.class)
    ResponseEntity<ApiResponse<Void>> handlingPropertyReferenceException(PropertyReferenceException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                .code(ErrorCode.INVALID_KEY.getCode())
                .message("Invalid sort property: " + exception.getPropertyName())
                .build());
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        String message = exception.getCustomMessage() != null ? exception.getCustomMessage() : errorCode.getMessage();
        return ResponseEntity.status(errorCode.getStatusCode()).body(ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(message)
                .build());
    }

    @ExceptionHandler(value = AccessDeniedException.class)
    ResponseEntity<ApiResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        return ResponseEntity.status(errorCode.getStatusCode())
                .body(ApiResponse.<Void>builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .build());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> handlingValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>builder()
                .code(ErrorCode.INVALID_KEY.getCode())
                .message("Validation failed")
                .result(errors)
                .build());
    }

    @ExceptionHandler(value = ResourceNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> handlingResourceNotFoundException(ResourceNotFoundException exception) {
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        return ResponseEntity.status(errorCode.getStatusCode()).body(ApiResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(exception.getMessage() != null ? exception.getMessage() : errorCode.getMessage())
                .build());
    }
}

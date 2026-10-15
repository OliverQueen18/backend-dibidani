package ml.dibidani.api;

import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.TooManyRequestsException;
import ml.dibidani.payment.PaymentGateway.WebhookVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import tools.jackson.core.JacksonException;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record ApiError(int status, String message, Map<String, String> errors, Instant timestamp) {}

  private ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> errors) {
    return ResponseEntity.status(status).body(new ApiError(status.value(), message, errors, Instant.now()));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiError> notFound(NotFoundException e) { return error(HttpStatus.NOT_FOUND, e.getMessage(), null); }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ApiError> noSuchElement(NoSuchElementException e) { return error(HttpStatus.NOT_FOUND, "Ressource introuvable", null); }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiError> conflict(ConflictException e) { return error(HttpStatus.CONFLICT, e.getMessage(), null); }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
    log.warn("Violation d'intégrité : {}", e.getMostSpecificCause().getMessage());
    return error(HttpStatus.CONFLICT, "Cette opération entre en conflit avec des données existantes", null);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  ResponseEntity<ApiError> tooMany(TooManyRequestsException e) { return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage(), null); }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> bad(IllegalArgumentException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage(), null); }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, JacksonException.class})
  ResponseEntity<ApiError> unreadable(Exception e) { return error(HttpStatus.BAD_REQUEST, "Requête mal formée", null); }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    Map<String, String> errors = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
    e.getBindingResult().getGlobalErrors().forEach(g -> errors.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
    return error(HttpStatus.BAD_REQUEST, "Données invalides", errors);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (20 Mo maximum)", null);
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ApiError> multipart(MultipartException e) {
    log.warn("Envoi multipart rejeté : {}", e.getMostSpecificCause().getMessage());
    String cause = String.valueOf(e.getMostSpecificCause().getMessage());
    if (cause.toLowerCase().contains("size") || cause.toLowerCase().contains("too large")) {
      return error(HttpStatus.PAYLOAD_TOO_LARGE, "Fichier trop volumineux (20 Mo maximum)", null);
    }
    return error(HttpStatus.BAD_REQUEST, "Envoi du fichier incomplet. Réessayez.", null);
  }

  @ExceptionHandler(IOException.class)
  ResponseEntity<ApiError> io(IOException e) {
    log.error("Écriture fichier : {}", e.getMessage());
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d'enregistrer l'image. Réessayez.", null);
  }

  @ExceptionHandler(WebhookVerificationException.class)
  ResponseEntity<ApiError> webhook(WebhookVerificationException e) {
    log.warn("Notification de paiement rejetée : {}", e.getMessage());
    return error(HttpStatus.UNAUTHORIZED, "Notification non authentifiée", null);
  }

  @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
  ResponseEntity<ApiError> forbidden(Exception e) { return error(HttpStatus.FORBIDDEN, "Accès refusé", null); }
}

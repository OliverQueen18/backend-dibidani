package ml.dibidani.common;

public class TooManyRequestsException extends RuntimeException {
  public TooManyRequestsException(String message) { super(message); }
}

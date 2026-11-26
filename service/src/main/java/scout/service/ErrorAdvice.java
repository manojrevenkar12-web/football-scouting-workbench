package scout.service;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Client mistakes come back as 400 with the problem; nothing internal leaks. */
@RestControllerAdvice
public class ErrorAdvice {

    @ExceptionHandler(ScoutController.BadRequest.class)
    ResponseEntity<ScoutController.Problems> bad(ScoutController.BadRequest e) {
        return ResponseEntity.badRequest().body(new ScoutController.Problems(List.of(e.getMessage())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ScoutController.Problems> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ScoutController.Problems(List.of("the request body is not valid JSON for this endpoint")));
    }
}

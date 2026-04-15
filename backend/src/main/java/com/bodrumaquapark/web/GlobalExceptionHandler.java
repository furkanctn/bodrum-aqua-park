package com.bodrumaquapark.web;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.CardNotFoundException;
import com.bodrumaquapark.exception.DuplicateCardUidException;
import com.bodrumaquapark.exception.InsufficientBalanceException;
import com.bodrumaquapark.exception.OutOfStockException;
import com.bodrumaquapark.exception.ProductNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CardNotFoundException.class)
	public ProblemDetail handleNotFound(CardNotFoundException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		pd.setTitle("Not Found");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler({ ProductNotFoundException.class })
	public ProblemDetail handleProductNotFound(RuntimeException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		pd.setTitle("Not Found");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler(DuplicateCardUidException.class)
	public ProblemDetail handleDuplicate(DuplicateCardUidException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		pd.setTitle("Conflict");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler(CardBlockedException.class)
	public ProblemDetail handleBlocked(CardBlockedException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		pd.setTitle("Forbidden");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler(InsufficientBalanceException.class)
	public ProblemDetail handleInsufficient(InsufficientBalanceException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		pd.setTitle("Insufficient Balance");
		pd.setType(URI.create("about:blank"));
		pd.setProperty("balance", ex.getBalance());
		pd.setProperty("required", ex.getRequired());
		return pd;
	}

	@ExceptionHandler(OutOfStockException.class)
	public ProblemDetail handleStock(OutOfStockException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		pd.setTitle("Out Of Stock");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
		pd.setTitle(status.getReasonPhrase());
		pd.setType(URI.create("about:blank"));
		return pd;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		String msg = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "));
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
		pd.setTitle("Validation Error");
		pd.setType(URI.create("about:blank"));
		return pd;
	}
}

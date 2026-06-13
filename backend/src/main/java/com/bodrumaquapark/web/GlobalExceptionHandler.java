package com.bodrumaquapark.web;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.exception.CardBlockedException;
import com.bodrumaquapark.exception.CardNotFoundException;
import com.bodrumaquapark.exception.DuplicateCardUidException;
import com.bodrumaquapark.exception.InsufficientBalanceException;
import com.bodrumaquapark.exception.OutOfStockException;
import com.bodrumaquapark.exception.PrinterNotAvailableException;
import com.bodrumaquapark.exception.ProductNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ClassCastException.class)
	public ProblemDetail handleClassCast(ClassCastException ex) {
		log.warn("ClassCastException", ex);
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Veri dönüşüm hatası. Oturumu kapatıp yeniden giriş yapın; devam ederse sunucu günlüğüne bakın.");
		pd.setTitle("Class Cast");
		pd.setType(URI.create("about:blank"));
		return pd;
	}

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

	@ExceptionHandler(PrinterNotAvailableException.class)
	public ProblemDetail handlePrinterNotAvailable(PrinterNotAvailableException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		pd.setTitle("Printer Not Available");
		pd.setType(URI.create("about:blank"));
		pd.setProperty("code", "printer_not_available");
		pd.setProperty("printerTarget", ex.getPrinterTarget());
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

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("DataIntegrityViolationException", ex);
		String detail = "Veritabanı kısıtı ihlali. Kayıt zaten var olabilir veya şema güncellemesi gerekebilir.";
		String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
		if (msg != null && !msg.isBlank()) {
			if (msg.contains("sale_area_id") || msg.contains("SALE_AREA_ID")) {
				detail = "Eski veritabanı şeması: menu_pages tablosunda sale_area_id sütunu kalmış. "
						+ "postgresql-catalog-v2-migration.sql scriptini çalıştırın veya uygulamayı yeniden başlatın.";
			} else if (msg.contains("unique") || msg.contains("UNIQUE") || msg.contains("Duplicate")) {
				detail = "Bu kayıt zaten mevcut (benzersiz kod veya ad çakışması).";
			}
		}
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
		pd.setTitle("Conflict");
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

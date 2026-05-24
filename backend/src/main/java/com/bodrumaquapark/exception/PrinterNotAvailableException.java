package com.bodrumaquapark.exception;

/**
 * Fiş yazıcı bağlı değilken satış yapılmaya çalışıldığında fırlatılır.
 */
public class PrinterNotAvailableException extends RuntimeException {

	private final String printerTarget;

	public PrinterNotAvailableException(String printerTarget) {
		super("Fiş yazıcı bağlı değil veya erişilemiyor. İşlem alınamadı.");
		this.printerTarget = printerTarget;
	}

	public String getPrinterTarget() {
		return printerTarget;
	}
}

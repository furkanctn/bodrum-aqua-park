package com.bodrumaquapark.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.service.PrinterService;

@RestController
@RequestMapping("/api/printer")
public class PrinterController {

	private final PrinterService printerService;

	public PrinterController(PrinterService printerService) {
		this.printerService = printerService;
	}

	/**
	 * JWT gerekmez — sunucuda fiş modülünün yüklü olduğunu ve doğru adres/port ile erişildiğini doğrular.
	 */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of(
				"ok", true,
				"module", "escpos-serial",
				"hint", "Yönetim sayfasını http://127.0.0.1:8081/admin.html adresinden açın; file:// ile açmayın.");
	}

	@GetMapping("/ports")
	public ResponseEntity<?> listPorts() {
		return ResponseEntity.ok(printerService.listSerialPorts());
	}

	/**
	 * Ürün satışı sonrası bilgi fişi — POS’tan çağrılır. Port gövdede veya app.printer.port ile.
	 */
	@PostMapping("/sale-receipt")
	public ResponseEntity<Map<String, Object>> saleReceipt(@RequestBody(required = false) PrinterSaleReceiptBody body) {
		if (body == null || body.lines() == null || body.lines().isEmpty()) {
			return error(HttpStatus.BAD_REQUEST, "lines gerekli");
		}
		if (body.lines().size() > 48) {
			return error(HttpStatus.BAD_REQUEST, "En fazla 48 satır");
		}
		String port = body.port() != null && !body.port().isBlank()
				? body.port().trim()
				: printerService.configuredPortOrNull();
		int baud = body.baudRate() != null && body.baudRate() > 0
				? body.baudRate()
				: printerService.configuredBaudRate();
		String mode = body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "nocut";

		Map<String, Object> ok = new LinkedHashMap<>();
		try {
			printerService.sendSaleInfoReceipt(port, baud, body.lines(), mode);
			ok.put("ok", true);
			ok.put("message", "Bilgi fişi gönderildi.");
			ok.put("port", port);
			ok.put("baudRate", baud);
			ok.put("mode", mode);
			return ResponseEntity.ok(ok);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return error(HttpStatus.INTERNAL_SERVER_ERROR, msg);
		}
	}

	@PostMapping("/test")
	public ResponseEntity<Map<String, Object>> test(@RequestBody(required = false) PrinterTestBody body) {
		String port = body != null && body.port() != null && !body.port().isBlank()
				? body.port().trim()
				: printerService.configuredPortOrNull();
		int baud = body != null && body.baudRate() != null && body.baudRate() > 0
				? body.baudRate()
				: printerService.configuredBaudRate();

		String mode = body != null && body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "full";

		Map<String, Object> ok = new LinkedHashMap<>();
		try {
			printerService.sendTestReceipt(port, baud, mode);
			ok.put("ok", true);
			ok.put("message", "Test fişi gönderildi.");
			ok.put("port", port);
			ok.put("baudRate", baud);
			ok.put("mode", mode);
			return ResponseEntity.ok(ok);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return error(HttpStatus.INTERNAL_SERVER_ERROR, msg);
		}
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ok", false);
		body.put("error", message);
		return ResponseEntity.status(status).body(body);
	}

	public record PrinterTestBody(String port, Integer baudRate, String mode) {
	}

	public record PrinterSaleReceiptBody(String port, Integer baudRate, String mode, List<String> lines) {
	}
}

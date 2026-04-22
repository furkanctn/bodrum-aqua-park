package com.bodrumaquapark.web;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
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
				"module", "escpos-serial-or-windows-spooler",
				"hint",
				"Windows: APP_PRINTER_WINDOWS_QUEUE ile yazıcı kuyruk adı (USB yazıcı, ham ESC/POS) — COM gerekmez. Aksi halde COM veya POS «Fiş USB» (Web Serial).");
	}

	@GetMapping("/ports")
	public ResponseEntity<?> listPorts() {
		return ResponseEntity.ok(printerService.listSerialPorts());
	}

	/** Yönetim: Windows'ta önce kuyruk adları, yoksa COM listesi ({@code kind}: windows|serial). */
	@GetMapping("/print-targets")
	public ResponseEntity<List<Map<String, String>>> printTargets() {
		return ResponseEntity.ok(printerService.listPrintTargets());
	}

	/**
	 * Kayıtlı / ortam varsayılanı fiş yazıcı adresi (POS sunucu tarafında çözümler).
	 */
	@GetMapping("/settings")
	public ResponseEntity<Map<String, Object>> getSettings() {
		return ResponseEntity.ok(printerService.readSettingsForDisplay());
	}

	/**
	 * Windows: Java'nın gördüğü yazıcı kuyrukları ve APP_PRINTER_WINDOWS_QUEUE eşleşmesi (JWT).
	 */
	@GetMapping("/windows-diagnostics")
	public ResponseEntity<Map<String, Object>> windowsDiagnostics() {
		return ResponseEntity.ok(printerService.windowsPrintDiagnostics());
	}

	/**
	 * Varsayılan fiş yazıcıyı veritabanına yazar — kurulumda bir kez; kasiyer COM seçmez.
	 * Yalnızca tam yönetici (ADMIN).
	 */
	@PutMapping("/settings")
	public ResponseEntity<Map<String, Object>> putSettings(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestBody PrinterPersistBody body) {
		if (role != RoleCode.ADMIN) {
			return error(HttpStatus.FORBIDDEN, "Bu işlem için tam yönetici gerekir");
		}
		if (body == null || body.port() == null || body.port().isBlank()) {
			return error(HttpStatus.BAD_REQUEST, "port gerekli");
		}
		int baud = body.baudRate() != null && body.baudRate() > 0 ? body.baudRate() : printerService.configuredBaudRate();
		try {
			printerService.savePrinterSettingsToDatabase(body.port(), baud);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, e.getMessage());
		}
		Map<String, Object> ok = new LinkedHashMap<>();
		ok.put("ok", true);
		ok.put("message", "Fiş yazıcı ayarı kaydedildi (sunucu COM modu; çoklu kasada POS «Fiş USB» kullanın).");
		ok.putAll(printerService.readSettingsForDisplay());
		return ResponseEntity.ok(ok);
	}

	/**
	 * Ürün satışı sonrası bilgi fişi — POS’tan çağrılır. Port önceliği: gövde → veritabanı kaydı → app.printer.*.
	 */
	/**
	 * Bilgi fişi ESC/POS baytları (Base64) — tarayıcı Web Serial ile yerel USB yazıcıya yazdırma için.
	 */
	@PostMapping("/sale-receipt-payload")
	public ResponseEntity<Map<String, Object>> saleReceiptPayload(@RequestBody(required = false) PrinterSaleReceiptBody body) {
		if (body == null || body.lines() == null || body.lines().isEmpty()) {
			return error(HttpStatus.BAD_REQUEST, "lines gerekli");
		}
		if (body.lines().size() > 48) {
			return error(HttpStatus.BAD_REQUEST, "En fazla 48 satır");
		}
		String mode = body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "nocut";
		try {
			byte[] raw = printerService.buildSaleInfoReceiptBytes(body.lines(), mode);
			String b64 = Base64.getEncoder().encodeToString(raw);
			Map<String, Object> ok = new LinkedHashMap<>();
			ok.put("ok", true);
			ok.put("base64", b64);
			ok.put("byteLength", raw.length);
			ok.put("suggestedBaud", printerService.effectiveBaud(body.baudRate()));
			ok.put("mode", mode);
			return ResponseEntity.ok(ok);
		} catch (IOException e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return error(HttpStatus.INTERNAL_SERVER_ERROR, msg);
		}
	}

	/** Test fişi ESC/POS baytları (Base64) — yerel USB denemesi için. */
	@PostMapping("/test-payload")
	public ResponseEntity<Map<String, Object>> testPayload(@RequestBody(required = false) PrinterTestBody body) {
		String mode = body != null && body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "minimal";
		try {
			byte[] raw = printerService.buildTestReceiptBytes(mode);
			String b64 = Base64.getEncoder().encodeToString(raw);
			Map<String, Object> ok = new LinkedHashMap<>();
			ok.put("ok", true);
			ok.put("base64", b64);
			ok.put("byteLength", raw.length);
			ok.put("suggestedBaud", printerService.effectiveBaud(body != null ? body.baudRate() : null));
			ok.put("mode", mode);
			return ResponseEntity.ok(ok);
		} catch (IOException e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			return error(HttpStatus.INTERNAL_SERVER_ERROR, msg);
		}
	}

	@PostMapping("/sale-receipt")
	public ResponseEntity<Map<String, Object>> saleReceipt(@RequestBody(required = false) PrinterSaleReceiptBody body) {
		if (body == null || body.lines() == null || body.lines().isEmpty()) {
			return error(HttpStatus.BAD_REQUEST, "lines gerekli");
		}
		if (body.lines().size() > 48) {
			return error(HttpStatus.BAD_REQUEST, "En fazla 48 satır");
		}
		String port = printerService.effectivePort(body.port());
		int baud = printerService.effectiveBaud(body.baudRate());
		String mode = body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "nocut";

		Map<String, Object> ok = new LinkedHashMap<>();
		try {
			String winOverride = body != null ? body.windowsQueueName() : null;
			printerService.sendSaleInfoReceipt(port, baud, body.lines(), mode, winOverride);
			ok.put("ok", true);
			ok.put("message", "Bilgi fişi gönderildi.");
			ok.put("port", port);
			ok.put("baudRate", baud);
			ok.put("mode", mode);
			if (winOverride != null && !winOverride.isBlank()) {
				ok.put("windowsQueueName", winOverride.trim());
			}
			if (printerService.usesWindowsQueueForJob(winOverride)) {
				ok.put("hint",
						"Windows kuyruk bazen ham ESC/POS’u yazıcıya iletmez; fiş boşsa COM port veya POS «Fiş USB» deneyin.");
			}
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
		String overridePort = body != null ? body.port() : null;
		Integer overrideBaud = body != null ? body.baudRate() : null;
		String winOverride = body != null ? body.windowsQueueName() : null;
		String port = printerService.effectivePort(overridePort);
		int baud = printerService.effectiveBaud(overrideBaud);

		String mode = body != null && body.mode() != null && !body.mode().isBlank() ? body.mode().trim() : "full";

		Map<String, Object> ok = new LinkedHashMap<>();
		try {
			printerService.sendTestReceipt(port, baud, mode, winOverride);
			ok.put("ok", true);
			ok.put("message", "Test fişi gönderildi.");
			ok.put("port", port);
			ok.put("baudRate", baud);
			ok.put("mode", mode);
			if (winOverride != null && !winOverride.isBlank()) {
				ok.put("windowsQueueName", winOverride.trim());
			}
			if (printerService.usesWindowsQueueForJob(winOverride)) {
				ok.put("hint",
						"Windows kuyruk bazen ham ESC/POS’u yazıcıya iletmez; fiş çıkmıyorsa Aygıtlar’da COM portu (USB–Seri) seçin veya POS’tan «Fiş USB» kullanın.");
			}
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

	public record PrinterTestBody(String port, Integer baudRate, String mode, String windowsQueueName) {
	}

	public record PrinterSaleReceiptBody(String port, Integer baudRate, String mode, List<String> lines,
			String windowsQueueName) {
	}

	public record PrinterPersistBody(String port, Integer baudRate) {
	}
}

package com.bodrumaquapark.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import java.awt.GraphicsEnvironment;

import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.PrinterName;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.config.PrinterProperties;
import com.bodrumaquapark.entity.AppSetting;
import com.bodrumaquapark.repository.AppSettingRepository;
import com.fazecast.jSerialComm.SerialPort;

@Service
public class PrinterService {

	private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

	private static final DocFlavor RAW_BYTES = new DocFlavor.BYTE_ARRAY("application/octet-stream");
	/** Bazı Windows sürücüleri yalnızca akış tabanlı ham bayt kabul eder. */
	private static final DocFlavor RAW_STREAM = new DocFlavor.INPUT_STREAM("application/octet-stream");

	static final String KEY_PRINTER_PORT = "printer.port";
	static final String KEY_PRINTER_BAUD = "printer.baud";

	private final PrinterProperties properties;
	private final AppSettingRepository appSettingRepository;

	public PrinterService(PrinterProperties properties, AppSettingRepository appSettingRepository) {
		this.properties = properties;
		this.appSettingRepository = appSettingRepository;
	}

	public List<Map<String, String>> listSerialPorts() {
		try {
			SerialPort[] ports = SerialPort.getCommPorts();
			List<Map<String, String>> list = new ArrayList<>();
			for (SerialPort p : ports) {
				Map<String, String> row = new LinkedHashMap<>();
				row.put("name", p.getSystemPortName());
				String desc = p.getDescriptivePortName();
				row.put("description", desc != null ? desc : "");
				list.add(row);
			}
			return list;
		} catch (Throwable t) {
			log.warn("Seri port listesi alınamadı (native kütüphane veya izin): {}", t.toString());
			return List.of();
		}
	}

	/**
	 * Yönetim hedef listesi: Windows'ta ham bayt kuyruğu varsa yalnızca bu adlar; yoksa seri portlar ({@code kind} alanı eklenir).
	 */
	public List<Map<String, String>> listPrintTargets() {
		String os = System.getProperty("os.name", "");
		boolean isWin = os != null && os.toLowerCase(Locale.ROOT).contains("win");
		if (isWin) {
			List<String> queues = listWindowsQueueNamesForRawBytes();
			if (!queues.isEmpty()) {
				List<Map<String, String>> out = new ArrayList<>(queues.size());
				for (String q : queues) {
					Map<String, String> row = new LinkedHashMap<>();
					row.put("kind", "windows");
					row.put("name", q);
					row.put("description", "Windows kuyruk (javax.print)");
					out.add(row);
				}
				return out;
			}
		}
		List<Map<String, String>> serial = listSerialPorts();
		List<Map<String, String>> out = new ArrayList<>(serial.size());
		for (Map<String, String> row : serial) {
			Map<String, String> ext = new LinkedHashMap<>(row);
			ext.put("kind", "serial");
			out.add(ext);
		}
		return out;
	}

	/**
	 * ESC/POS test fişi baytları (seri port veya tarayıcı Web Serial).
	 *
	 * @param mode "full" | "nocut" | "minimal" — minimal/kesimsiz donanım teşhisi için
	 */
	public byte[] buildTestReceiptBytes(String mode) throws IOException {
		String m = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "full";
		return switch (m) {
			case "minimal" -> EscPosUtil.buildTestReceiptMinimal();
			case "nocut" -> EscPosUtil.buildTestReceiptNoCut();
			default -> EscPosUtil.buildTestReceiptFull();
		};
	}

	public void sendTestReceipt(String portName, int baudRate, String mode, String windowsQueueOverride) throws Exception {
		String m = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "full";
		byte[] data = buildTestReceiptBytes(m);
		Optional<String> win = resolveWindowsQueueForJob(windowsQueueOverride);
		if (win.isPresent()) {
			requireWindowsOs();
			writeRawToWindowsPrintQueue(win.get(), data);
			log.info("Fiş testi (Windows kuyruk {}): {} bayt, mode={}, hex[ilk 32]={}", win.get(), data.length, m,
					toHexPrefix(data, 32));
			return;
		}
		if (portName == null || portName.isBlank()) {
			throw new IllegalArgumentException("Port gerekli (örn. COM3 veya /dev/cu.usbserial-…).");
		}
		int baud = baudRate > 0 ? baudRate : properties.getBaudRate();
		writeRawToSerialPort(portName.trim(), baud, data);
		log.info("Fiş testi gönderildi: {} bayt, baud={}, mode={}, hex[ilk 32]={}", data.length, baud, m,
				toHexPrefix(data, 32));
	}

	/** Tarayıcı Web Serial ile gönderim için bilgi fişi ESC/POS baytları. */
	public byte[] buildSaleInfoReceiptBytes(List<String> lines, String mode) throws IOException {
		String m = mode != null && !mode.isBlank() ? mode.trim().toLowerCase(Locale.ROOT) : "nocut";
		return EscPosUtil.buildSaleInfoReceipt(lines, m);
	}

	/** Ürün satışı bilgi fişi (ESC/POS). */
	public void sendSaleInfoReceipt(String portName, int baudRate, List<String> lines, String mode,
			String windowsQueueOverride) throws Exception {
		String m = mode != null && !mode.isBlank() ? mode.trim().toLowerCase(Locale.ROOT) : "nocut";
		byte[] data = buildSaleInfoReceiptBytes(lines, m);
		Optional<String> win = resolveWindowsQueueForJob(windowsQueueOverride);
		if (win.isPresent()) {
			requireWindowsOs();
			writeRawToWindowsPrintQueue(win.get(), data);
			log.info("Bilgi fişi (Windows kuyruk {}): {} satır, {} bayt", win.get(), lines.size(), data.length);
			return;
		}
		if (portName == null || portName.isBlank()) {
			throw new IllegalArgumentException("Port gerekli (örn. COM3 veya /dev/cu.usbserial-…).");
		}
		int baud = baudRate > 0 ? baudRate : properties.getBaudRate();
		writeRawToSerialPort(portName.trim(), baud, data);
		log.info("Bilgi fişi gönderildi: {} satır, {} bayt, baud={}", lines.size(), data.length, baud);
	}

	private void writeRawToSerialPort(String portName, int baud, byte[] data) throws Exception {
		SerialPort port = SerialPort.getCommPort(portName);
		port.setBaudRate(baud);
		port.setNumDataBits(8);
		port.setNumStopBits(SerialPort.ONE_STOP_BIT);
		port.setParity(SerialPort.NO_PARITY);
		port.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 0, 5000);

		if (!port.openPort()) {
			throw new IllegalStateException("Port açılamadı: " + portName + " (başka uygulama kullanıyor olabilir)");
		}
		try {
			port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
			try {
				port.setDTR();
				port.setRTS();
			} catch (Throwable ignored) {
			}
			int n = port.writeBytes(data, data.length);
			if (n != data.length) {
				throw new IllegalStateException("Yazma tamamlanmadı: " + n + "/" + data.length + " bayt");
			}
			try {
				port.flushIOBuffers();
			} catch (Throwable ignored) {
			}
			Thread.sleep(200);
		} finally {
			port.closePort();
		}
	}

	/** Windows yazıcı kuyruğu adı (properties / ortam). Veritabanında tutulmaz; çoklu kasa için PC başına ortam. */
	public Optional<String> effectiveWindowsQueueName() {
		String q = properties.getWindowsQueueName();
		if (q != null && !q.isBlank()) {
			return Optional.of(q.trim());
		}
		return Optional.empty();
	}

	/**
	 * İstekte verilen kuyruk adı önceliklidir; boşsa ortam {@code APP_PRINTER_WINDOWS_QUEUE} kullanılır.
	 */
	/** Bu istek ortam/override ile Windows kuyruk (javax.print) yoluna gidecek mi — yanıt mesajları için. */
	public boolean usesWindowsQueueForJob(String overrideFromRequest) {
		return resolveWindowsQueueForJob(overrideFromRequest).isPresent();
	}

	private Optional<String> resolveWindowsQueueForJob(String overrideFromRequest) {
		if (overrideFromRequest != null && !overrideFromRequest.isBlank()) {
			requireWindowsOs();
			String q = overrideFromRequest.trim();
			PrintService ps = findPrintServiceByQueueName(q);
			if (ps == null) {
				throw new IllegalArgumentException("Windows yazıcı kuyruğu bulunamadı: \"" + q + "\"");
			}
			String canonical = ps.getName();
			return Optional.of(canonical != null && !canonical.isBlank() ? canonical : q);
		}
		return effectiveWindowsQueueName();
	}

	public boolean usesWindowsSpooler() {
		return effectiveWindowsQueueName().isPresent();
	}

	/**
	 * Windows POS kurulumu: javax.print ile ham bayt (BYTE_ARRAY) kabul eden kuyruklar ve
	 * {@code APP_PRINTER_WINDOWS_QUEUE} / {@code app.printer.windows-queue-name} eşleşmesi.
	 */
	public Map<String, Object> windowsPrintDiagnostics() {
		Map<String, Object> m = new LinkedHashMap<>();
		String os = System.getProperty("os.name", "?");
		boolean win = os != null && os.toLowerCase(Locale.ROOT).contains("win");
		m.put("osName", os);
		m.put("isWindows", win);
		Optional<String> cfg = effectiveWindowsQueueName();
		String cfgStr = cfg.orElse(null);
		m.put("configuredQueueName", cfgStr);

		boolean awtHeadless = true;
		try {
			awtHeadless = GraphicsEnvironment.isHeadless();
		} catch (Throwable ignored) {
		}
		m.put("awtHeadless", awtHeadless);
		if (win && awtHeadless) {
			m.put("headlessNote",
					"Java AWT headless modunda; Windows yazıcı kuyruğuna iş düşmeyebilir. BodrumAquaPark.bat (JAVA_OPTS_SERVER) veya spring.main.headless=false ile başlatın.");
		}

		if (!win) {
			m.put("queues", List.of());
			m.put("queueCount", 0);
			m.put("configuredMatchesQueue", false);
			m.put("success", false);
			m.put("message",
					"Bu makine Windows değil; kuyruk listesi yalnızca kasa Windows PC'de dolar. Ortamda tanımlı ad: "
							+ (cfgStr != null ? "\"" + cfgStr + "\"" : "(yok)"));
			return m;
		}

		List<String> queues = listWindowsQueueNamesForRawBytes();
		m.put("queues", queues);
		m.put("queueCount", queues.size());
		boolean match = cfgStr != null && !cfgStr.isBlank()
				&& queues.stream().anyMatch(n -> cfgStr.equalsIgnoreCase(n));
		m.put("configuredMatchesQueue", match);

		if (cfgStr == null || cfgStr.isBlank()) {
			m.put("success", false);
			m.put("message", queues.isEmpty()
					? "APP_PRINTER_WINDOWS_QUEUE tanımlı değil ve Java ham bayt ile yazıcı bulamadı."
					: "APP_PRINTER_WINDOWS_QUEUE tanımlı değil. Aşağıdaki listeden tam adı .bat veya ortam değişkenine yazın.");
		} else if (match) {
			m.put("success", true);
			m.put("message",
					"Kuyruk Java'da bulundu. Fiş yine çıkmıyorsa sürücü ham ESC/POS iletmiyor olabilir; COM port veya POS «Fiş USB» deneyin.");
		} else {
			m.put("success", false);
			m.put("message",
					"Ayarlı ad listede yok: \"" + cfgStr
							+ "\". Aygıtlar ve Yazıcılar adıyla birebir eşleşmeli (boşluk / yazım farkı).");
		}
		return m;
	}

	private List<String> listWindowsQueueNamesForRawBytes() {
		try {
			PrintService[] all = PrintServiceLookup.lookupPrintServices(RAW_BYTES, null);
			if (all == null || all.length == 0) {
				return List.of();
			}
			List<String> names = new ArrayList<>(all.length);
			for (PrintService ps : all) {
				if (ps == null) {
					continue;
				}
				String n = ps.getName();
				if (n != null && !n.isBlank()) {
					names.add(n);
				}
			}
			Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
			return names;
		} catch (Throwable t) {
			log.warn("Windows yazıcı listesi alınamadı: {}", t.toString());
			return List.of();
		}
	}

	private void requireWindowsOs() {
		String os = System.getProperty("os.name", "");
		if (os == null || !os.toLowerCase(Locale.ROOT).contains("win")) {
			throw new IllegalStateException(
					"Windows yazıcı kuyruğu yalnızca Windows'ta kullanılabilir (javax.print). Bu makinede OS: "
							+ (os != null ? os : "?"));
		}
	}

	/**
	 * Ham ESC/POS baytlarını Windows yazıcı kuyruğuna gönderir (sürücü ham veriyi yazıcıya iletmeli).
	 */
	private void writeRawToWindowsPrintQueue(String queueName, byte[] data) throws PrintException {
		PrintService service = findPrintServiceByQueueName(queueName);
		if (service == null) {
			throw new IllegalStateException("Yazıcı kuyruğu bulunamadı: \"" + queueName
					+ "\". Aygıtlar ve Yazıcılar'daki adı birebir kullanın (büyük/küçük harf duyarsız eşleşir).");
		}
		DocFlavor flavor;
		Object printData;
		if (service.isDocFlavorSupported(RAW_BYTES)) {
			flavor = RAW_BYTES;
			printData = data;
		} else if (service.isDocFlavorSupported(RAW_STREAM)) {
			flavor = RAW_STREAM;
			printData = new ByteArrayInputStream(data);
		} else {
			throw new IllegalStateException("Yazıcı ham bayt (application/octet-stream) desteklemiyor: \""
					+ service.getName() + "\". COM port veya başka bir sürücü deneyin.");
		}
		DocPrintJob job = service.createPrintJob();
		SimpleDoc doc = new SimpleDoc(printData, flavor, null);
		PrintRequestAttributeSet jobAttrs = new HashPrintRequestAttributeSet();
		jobAttrs.add(new JobName("Bodrum POS", Locale.ROOT));

		CountDownLatch done = new CountDownLatch(1);
		AtomicBoolean failed = new AtomicBoolean(false);
		job.addPrintJobListener(new PrintJobAdapter() {
			@Override
			public void printJobFailed(PrintJobEvent ev) {
				failed.set(true);
				log.warn("Windows yazdırma hatası: {}", service.getName());
				done.countDown();
			}

			@Override
			public void printJobCanceled(PrintJobEvent ev) {
				failed.set(true);
				log.warn("Windows yazdırma iptal: {}", service.getName());
				done.countDown();
			}

			@Override
			public void printJobNoMoreEvents(PrintJobEvent ev) {
				done.countDown();
			}
		});
		job.print(doc, jobAttrs);
		log.info("Windows yazdırma işi gönderildi: kuyruk={}, flavor={}, bayt={}", service.getName(), flavor, data.length);
		try {
			if (!done.await(90, TimeUnit.SECONDS)) {
				log.warn("Windows yazdırma olayı 90 sn içinde gelmedi ({}); iş kuyrukta olabilir.", service.getName());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PrintException("Yazdırma beklemesi kesildi");
		}
		if (failed.get()) {
			throw new PrintException("Windows yazıcı kuyruğu işi başarısız veya iptal: " + service.getName());
		}
		try {
			Thread.sleep(400);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		log.info("Windows kuyruk yazdırma tamamlandı: {}", service.getName());
	}

	private static PrintService findPrintServiceByQueueName(String queueName) {
		// PrinterName bir PrintServiceAttribute'tır; PrintRequestAttributeSet ile kullanılırsa
		// Windows javax.print içinde ClassCastException oluşabilir.
		PrintServiceAttributeSet filter = new HashPrintServiceAttributeSet();
		filter.add(new PrinterName(queueName, null));
		PrintService[] byName = PrintServiceLookup.lookupPrintServices(RAW_BYTES, filter);
		if (byName != null && byName.length > 0) {
			return byName[0];
		}
		PrintService[] all = PrintServiceLookup.lookupPrintServices(RAW_BYTES, null);
		if (all == null) {
			return null;
		}
		for (PrintService ps : all) {
			if (ps != null && queueName.equalsIgnoreCase(ps.getName())) {
				return ps;
			}
		}
		return null;
	}

	/** Ortam değişkeni / application.properties (boş olabilir). */
	public String configuredPortOrNull() {
		String p = properties.getPort();
		return p != null && !p.isBlank() ? p.trim() : null;
	}

	public int configuredBaudRate() {
		return properties.getBaudRate();
	}

	public Optional<String> databasePortOrEmpty() {
		return appSettingRepository.findBySettingKey(KEY_PRINTER_PORT).map(AppSetting::getSettingValue)
				.map(String::trim)
				.filter(s -> !s.isEmpty());
	}

	public Optional<Integer> databaseBaudOrEmpty() {
		return appSettingRepository.findBySettingKey(KEY_PRINTER_BAUD).map(AppSetting::getSettingValue).map(String::trim)
				.filter(s -> !s.isEmpty())
				.flatMap(PrinterService::parsePositiveInt);
	}

	/**
	 * Fiş gönderiminde kullanılacak port: istek gövdesi (varsa) → veritabanı → ortam/properties.
	 */
	public String effectivePort(String requestPortOverride) {
		if (requestPortOverride != null && !requestPortOverride.isBlank()) {
			return requestPortOverride.trim();
		}
		Optional<String> db = databasePortOrEmpty();
		if (db.isPresent()) {
			return db.get();
		}
		return configuredPortOrNull();
	}

	public int effectiveBaud(Integer requestBaudOverride) {
		if (requestBaudOverride != null && requestBaudOverride > 0) {
			return requestBaudOverride;
		}
		Optional<Integer> db = databaseBaudOrEmpty();
		if (db.isPresent()) {
			return db.get();
		}
		return configuredBaudRate();
	}

	/**
	 * Arayüz için: hangi kaynaktan geldiği (database | environment | unset).
	 */
	public Map<String, Object> readSettingsForDisplay() {
		Optional<String> win = effectiveWindowsQueueName();
		if (win.isPresent()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("port", null);
			m.put("baudRate", configuredBaudRate());
			m.put("source", "windows-queue");
			m.put("windowsQueueName", win.get());
			return m;
		}
		Optional<String> dbPort = databasePortOrEmpty();
		Optional<Integer> dbBaud = databaseBaudOrEmpty();
		if (dbPort.isPresent()) {
			int baud = dbBaud.orElseGet(this::configuredBaudRate);
			return Map.of("port", dbPort.get(), "baudRate", baud, "source", "database");
		}
		String envPort = configuredPortOrNull();
		if (envPort != null) {
			return Map.of("port", envPort, "baudRate", configuredBaudRate(), "source", "environment");
		}
		Map<String, Object> unset = new LinkedHashMap<>();
		unset.put("port", null);
		unset.put("baudRate", configuredBaudRate());
		unset.put("source", "unset");
		return unset;
	}

	@Transactional
	public void savePrinterSettingsToDatabase(String port, int baudRate) {
		if (port == null || port.isBlank()) {
			throw new IllegalArgumentException("Port boş olamaz.");
		}
		if (baudRate <= 0) {
			throw new IllegalArgumentException("Geçerli bir baud hızı girin.");
		}
		String p = port.trim();
		upsert(KEY_PRINTER_PORT, p);
		upsert(KEY_PRINTER_BAUD, Integer.toString(baudRate));
		log.info("Fiş yazıcı ayarı veritabanına kaydedildi: port={}, baud={}", p, baudRate);
	}

	private void upsert(String key, String value) {
		Optional<AppSetting> opt = appSettingRepository.findBySettingKey(key);
		if (opt.isPresent()) {
			AppSetting row = opt.get();
			row.setSettingValue(value);
			appSettingRepository.save(row);
		} else {
			appSettingRepository.save(new AppSetting(key, value));
		}
	}

	private static Optional<Integer> parsePositiveInt(String s) {
		try {
			int n = Integer.parseInt(s);
			return n > 0 ? Optional.of(n) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static String toHexPrefix(byte[] data, int max) {
		if (data == null || data.length == 0) {
			return "";
		}
		int n = Math.min(max, data.length);
		StringBuilder sb = new StringBuilder(n * 3);
		for (int i = 0; i < n; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(String.format("%02X", data[i]));
		}
		if (data.length > n) {
			sb.append(" …");
		}
		return sb.toString();
	}
}

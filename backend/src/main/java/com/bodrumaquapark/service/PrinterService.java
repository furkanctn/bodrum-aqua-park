package com.bodrumaquapark.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bodrumaquapark.config.PrinterProperties;
import com.fazecast.jSerialComm.SerialPort;

@Service
public class PrinterService {

	private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

	private final PrinterProperties properties;

	public PrinterService(PrinterProperties properties) {
		this.properties = properties;
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
	 * ESC/POS test fişini belirtilen seri porta yazar.
	 *
	 * @param mode "full" | "nocut" | "minimal" — minimal/kesimsiz donanım teşhisi için
	 */
	public void sendTestReceipt(String portName, int baudRate, String mode) throws Exception {
		if (portName == null || portName.isBlank()) {
			throw new IllegalArgumentException("Port gerekli (örn. COM3 veya /dev/cu.usbserial-…).");
		}
		int baud = baudRate > 0 ? baudRate : properties.getBaudRate();
		String m = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "full";
		byte[] data = switch (m) {
			case "minimal" -> EscPosUtil.buildTestReceiptMinimal();
			case "nocut" -> EscPosUtil.buildTestReceiptNoCut();
			default -> EscPosUtil.buildTestReceiptFull();
		};

		writeRawToSerialPort(portName.trim(), baud, data);
		log.info("Fiş testi gönderildi: {} bayt, baud={}, mode={}, hex[ilk 32]={}", data.length, baud, m,
				toHexPrefix(data, 32));
	}

	/** Ürün satışı bilgi fişi (ESC/POS). */
	public void sendSaleInfoReceipt(String portName, int baudRate, List<String> lines, String mode) throws Exception {
		if (portName == null || portName.isBlank()) {
			throw new IllegalArgumentException("Port gerekli (örn. COM3 veya /dev/cu.usbserial-…).");
		}
		int baud = baudRate > 0 ? baudRate : properties.getBaudRate();
		String m = mode != null && !mode.isBlank() ? mode.trim().toLowerCase(Locale.ROOT) : "nocut";
		byte[] data = EscPosUtil.buildSaleInfoReceipt(lines, m);
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

	public String configuredPortOrNull() {
		String p = properties.getPort();
		return p != null && !p.isBlank() ? p.trim() : null;
	}

	public int configuredBaudRate() {
		return properties.getBaudRate();
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

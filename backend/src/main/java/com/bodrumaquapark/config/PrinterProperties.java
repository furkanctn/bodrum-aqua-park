package com.bodrumaquapark.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.printer")
public class PrinterProperties {

	/**
	 * Windows: Aygıt Yöneticisi'ndeki COM adı (örn. COM3). Boşsa yalnızca API'de port gönderilir.
	 */
	private String port = "";

	/**
	 * Sewoo genelde 9600 veya 115200; sürücüye göre değişir.
	 */
	private int baudRate = 9600;

	/**
	 * Windows: Aygıtlar ve Yazıcılar'daki tam yazıcı adı (ESC/POS ham bayt). Doluysa COM kullanılmaz.
	 * Çoklu kasa: her PC'de APP_PRINTER_WINDOWS_QUEUE ile ayrı isim.
	 */
	private String windowsQueueName = "";

	public String getWindowsQueueName() {
		return windowsQueueName;
	}

	public void setWindowsQueueName(String windowsQueueName) {
		this.windowsQueueName = windowsQueueName != null ? windowsQueueName : "";
	}

	public String getPort() {
		return port;
	}

	public void setPort(String port) {
		this.port = port != null ? port : "";
	}

	public int getBaudRate() {
		return baudRate;
	}

	public void setBaudRate(int baudRate) {
		this.baudRate = baudRate;
	}
}

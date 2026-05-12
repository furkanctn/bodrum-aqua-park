package com.bodrumaquapark.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Merkezi tanılama çıktısı: Windows üretimde {@code C:\Aquapark\logs}, diğer ortamlarda {@code ~/Aquapark/logs}.
 * I/O hatalarında stderr'e düşer; sonsuz döngü yok.
 */
public final class AquaparkDiagnosticLog {

	private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private AquaparkDiagnosticLog() {
	}

	public static Path resolveLogDirectory() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			if (os.contains("win")) {
				return Path.of("C:", "Aquapark", "logs");
			}
			return Path.of(System.getProperty("user.home"), "Aquapark", "logs");
		} catch (Exception e) {
			return Path.of(System.getProperty("java.io.tmpdir", "."));
		}
	}

	/**
	 * @param category örn. {@code launcher}, {@code update}, {@code db}
	 */
	public static void append(String category, String message, Throwable error) {
		try {
			Path dir = resolveLogDirectory();
			Files.createDirectories(dir);
			String day = LocalDate.now().toString();
			Path file = dir.resolve(safeSegment(category) + "_" + day + ".log");
			StringBuilder line = new StringBuilder();
			line.append(LocalDateTime.now().format(TS)).append(" [").append(category).append("] ").append(message);
			if (error != null) {
				line.append(System.lineSeparator());
				StringWriter sw = new StringWriter();
				error.printStackTrace(new PrintWriter(sw));
				line.append(sw);
			}
			line.append(System.lineSeparator());
			Files.writeString(file, line.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception io) {
			try {
				System.err.println("[AquaparkDiagnosticLog] " + message);
				if (error != null) {
					error.printStackTrace(System.err);
				}
				io.printStackTrace(System.err);
			} catch (Exception ignored) {
			}
		}
	}

	private static String safeSegment(String category) {
		if (category == null || category.isBlank()) {
			return "app";
		}
		String t = category.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
		return t.isEmpty() ? "app" : t;
	}
}

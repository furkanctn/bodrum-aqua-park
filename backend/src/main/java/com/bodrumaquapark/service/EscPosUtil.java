package com.bodrumaquapark.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * ESC/POS termal yazıcılar — çoğu model US-ASCII + CR/LF ister.
 */
public final class EscPosUtil {

	private EscPosUtil() {
	}

	/** Tam test: başlık + tarih + besleme + tam kesim. */
	public static byte[] buildTestReceipt() throws IOException {
		return buildTestReceiptFull();
	}

	public static byte[] buildTestReceiptFull() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] { 0x1B, 0x40 });
		out.write(new byte[] { 0x1B, 0x61, 0x01 });
		writeAsciiLine(out, "Bodrum Aqua Park");
		out.write(new byte[] { 0x1B, 0x61, 0x00 });
		writeAsciiLine(out, "TEST FISI");
		writeAsciiLine(out, "----------------");
		String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
		writeAsciiLine(out, when);
		// Ek besleme + ESC d (bazı modellerde kesimden önce görünür çıktı için)
		out.write(new byte[] { 0x1B, 0x64, 0x08 });
		for (int i = 0; i < 5; i++) {
			out.write(new byte[] { 0x0D, 0x0A });
		}
		out.write(new byte[] { 0x1D, 0x56, 0x00 });
		out.write(new byte[] { 0x0D, 0x0A });
		return out.toByteArray();
	}

	/** Aynı metin, kesim komutu yok (yazıcı kesimi yutuyorsa teşhis için). */
	public static byte[] buildTestReceiptNoCut() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] { 0x1B, 0x40 });
		out.write(new byte[] { 0x1B, 0x61, 0x01 });
		writeAsciiLine(out, "Bodrum Aqua Park");
		out.write(new byte[] { 0x1B, 0x61, 0x00 });
		writeAsciiLine(out, "TEST FISI (NO CUT)");
		writeAsciiLine(out, "----------------");
		String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
		writeAsciiLine(out, when);
		for (int i = 0; i < 8; i++) {
			out.write(new byte[] { 0x0D, 0x0A });
		}
		return out.toByteArray();
	}

	/** En az komut: init + kısa metin + boşluk; kesim yok. */
	public static byte[] buildTestReceiptMinimal() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] { 0x1B, 0x40 });
		writeAsciiLine(out, "TEST MINIMAL");
		for (int i = 0; i < 12; i++) {
			out.write(new byte[] { 0x0D, 0x0A });
		}
		return out.toByteArray();
	}

	/**
	 * Bilgi fişi — ortada BODRUM AQUA PARK başlığı, altında etiket: değer satırları.
	 *
	 * @param mode "full" kesimli; "nocut" / "minimal" kesimsiz
	 */
	public static byte[] buildSaleInfoReceipt(List<String> lines, String mode) throws IOException {
		if (lines == null || lines.isEmpty()) {
			throw new IllegalArgumentException("En az bir satır gerekli");
		}
		String m = mode != null ? mode.trim().toLowerCase(Locale.ROOT) : "nocut";
		boolean cut = "full".equals(m);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[] { 0x1B, 0x40 });
		out.write(new byte[] { 0x1B, 0x61, 0x01 });
		out.write(new byte[] { 0x1B, 0x21, 0x30 });
		writeAsciiLine(out, "BODRUM AQUA PARK");
		out.write(new byte[] { 0x1B, 0x21, 0x00 });
		writeBlankLines(out, 2);
		out.write(new byte[] { 0x1B, 0x61, 0x00 });
		writeAsciiLine(out, "----------------");
		writeBlankLine(out);
		int start = 0;
		if (!lines.isEmpty() && isReceiptHeaderLine(lines.get(0))) {
			start = 1;
		}
		for (int i = start; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line == null || line.isBlank()) {
				continue;
			}
			writeAsciiLine(out, toAsciiReceiptLine(line));
		}
		writeAsciiLine(out, "----------------");
		writeBlankLines(out, 2);
		out.write(new byte[] { 0x1B, 0x61, 0x01 });
		writeAsciiLine(out, "Mali degeri yoktur");
		out.write(new byte[] { 0x1B, 0x61, 0x00 });
		writeBlankLines(out, 4);
		if (cut) {
			out.write(new byte[] { 0x1D, 0x56, 0x00 });
			out.write(new byte[] { 0x0D, 0x0A });
		}
		return out.toByteArray();
	}

	private static boolean isReceiptHeaderLine(String line) {
		if (line == null) {
			return false;
		}
		String n = line.trim().toUpperCase(Locale.ROOT).replace('İ', 'I');
		return n.contains("BODRUM") && n.contains("AQUA");
	}

	/** Termal genişlik için güvenli ASCII; Türkçe ve ₺ yaklaşık dönüşümü. */
	public static String toAsciiReceiptLine(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case 'ı' -> sb.append('i');
				case 'İ' -> sb.append('I');
				case 'ğ' -> sb.append('g');
				case 'Ğ' -> sb.append('G');
				case 'ü' -> sb.append('u');
				case 'Ü' -> sb.append('U');
				case 'ş' -> sb.append('s');
				case 'Ş' -> sb.append('S');
				case 'ö' -> sb.append('o');
				case 'Ö' -> sb.append('O');
				case 'ç' -> sb.append('c');
				case 'Ç' -> sb.append('C');
				case '₺' -> sb.append("TL");
				case '—', '–', '─' -> sb.append('-');
				default -> {
					if (c >= 32 && c < 127) {
						sb.append(c);
					} else if (Character.isWhitespace(c)) {
						sb.append(' ');
					}
					// diğer Unicode: atla (yazıcı bozulmasın)
				}
			}
		}
		String s = sb.toString().trim();
		if (s.length() > 48) {
			return s.substring(0, 48);
		}
		return s;
	}

	private static void writeAsciiLine(ByteArrayOutputStream out, String text) throws IOException {
		String safe = text == null ? "" : text;
		byte[] raw = safe.getBytes(StandardCharsets.US_ASCII);
		out.write(raw);
		out.write(new byte[] { 0x0D, 0x0A });
	}

	private static void writeBlankLine(ByteArrayOutputStream out) throws IOException {
		out.write(new byte[] { 0x0D, 0x0A });
	}

	private static void writeBlankLines(ByteArrayOutputStream out, int count) throws IOException {
		for (int i = 0; i < count; i++) {
			writeBlankLine(out);
		}
	}
}

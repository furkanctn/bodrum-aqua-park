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
	 * Ürün satışı bilgi fişi — metinler US-ASCII’ye indirgenir (Türkçe karakterler yaklaşık eşlenir).
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
		writeAsciiLine(out, toAsciiReceiptLine(lines.get(0)));
		out.write(new byte[] { 0x1B, 0x61, 0x00 });
		for (int i = 1; i < lines.size(); i++) {
			writeAsciiLine(out, toAsciiReceiptLine(lines.get(i)));
		}
		String when = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
		writeAsciiLine(out, "");
		writeAsciiLine(out, toAsciiReceiptLine(when));
		for (int i = 0; i < 4; i++) {
			out.write(new byte[] { 0x0D, 0x0A });
		}
		if (cut) {
			out.write(new byte[] { 0x1D, 0x56, 0x00 });
			out.write(new byte[] { 0x0D, 0x0A });
		}
		return out.toByteArray();
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
}

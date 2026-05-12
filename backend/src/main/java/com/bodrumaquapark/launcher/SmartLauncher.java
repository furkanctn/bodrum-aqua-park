package com.bodrumaquapark.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.bodrumaquapark.util.AquaparkDiagnosticLog;

/**
 * Offline-first POS: açılışta merkez paylaşımdaki {@code latest.txt} ve sürüm klasöründeki {@code app.jar}
 * ile yerel kurulumu eşitler; ana uygulamayı ayrı JVM'de başlatıp çıkar.
 * <p>
 * Ortam değişkenleri: {@code AQUAPARK_UPDATE_ROOT} (varsayılan {@code C:/Aquapark_Update}),
 * {@code AQUAPARK_LOCAL_DIR} (Windows varsayılan {@code C:/Aquapark}),
 * {@code AQUAPARK_LOCAL_JAR} (yerel çalıştırılacak JAR adı, varsayılan {@code app.jar}).
 * <p>
 * Sıcak yeniden başlatma: ilk argüman {@code --hot-restart} ise dosya kilitleri için kısa gecikme uygulanır.
 */
public final class SmartLauncher {

	private SmartLauncher() {
	}

	public static void main(String[] args) {
		boolean hotRestart = args.length > 0 && "--hot-restart".equals(args[0]);
		if (hotRestart) {
			sleepQuiet(3000);
		}
		Path localRoot = resolveLocalRoot();
		String localJarName = envOr("AQUAPARK_LOCAL_JAR", "app.jar");
		Path localJar = localRoot.resolve(localJarName);
		Path versionFile = localRoot.resolve(envOr("AQUAPARK_VERSION_FILE", "version.txt"));
		Path updateRoot = resolveUpdateRoot();
		Path latestFile = updateRoot.resolve(envOr("AQUAPARK_LATEST_FILE", "latest.txt"));
		String remoteJarName = envOr("AQUAPARK_REMOTE_JAR", "app.jar");

		String remoteVersion = null;
		try {
			if (Files.isReadable(latestFile)) {
				remoteVersion = Files.readString(latestFile).trim();
			}
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("launcher", "latest.txt okunamadı: " + latestFile, e);
		}

		String localVersion = readLocalVersion(versionFile);

		boolean needSync = remoteVersion != null && !remoteVersion.isEmpty()
				&& !remoteVersion.equals(localVersion);

		if (needSync) {
			Path sourceJar = updateRoot.resolve(remoteVersion).resolve(remoteJarName);
			try {
				Files.createDirectories(localRoot);
				if (Files.isReadable(sourceJar)) {
					Path tmp = localRoot.resolve(localJar.getFileName().toString() + ".tmp");
					Files.copy(sourceJar, tmp, StandardCopyOption.REPLACE_EXISTING);
					Files.move(tmp, localJar, StandardCopyOption.REPLACE_EXISTING);
					Files.writeString(versionFile, remoteVersion);
					AquaparkDiagnosticLog.append("launcher",
							"Senkron: " + sourceJar + " -> " + localJar + " sürüm=" + remoteVersion, null);
				} else {
					AquaparkDiagnosticLog.append("launcher",
							"Kaynak JAR bulunamadı, yerel sürümle devam: " + sourceJar, null);
				}
			} catch (Exception e) {
				AquaparkDiagnosticLog.append("launcher", "JAR kopyalama hatası; yerel devam.", e);
			}
		}

		if (!Files.isReadable(localJar)) {
			AquaparkDiagnosticLog.append("launcher", "Yerel JAR yok veya okunamıyor: " + localJar, null);
			System.err.println("Aquapark: yerel uygulama bulunamadı: " + localJar);
			System.exit(1);
			return;
		}

		Path javaBin = resolveJavaExecutable();
		List<String> cmd = new ArrayList<>();
		cmd.add(javaBin.toString());
		cmd.add("-jar");
		cmd.add(localJar.toAbsolutePath().toString());
		for (String a : filterPassthroughArgs(args, hotRestart)) {
			cmd.add(a);
		}
		try {
			new ProcessBuilder(cmd).directory(localRoot.toFile()).inheritIO().start();
			AquaparkDiagnosticLog.append("launcher", "Ana uygulama başlatıldı, launcher çıkıyor.", null);
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("launcher", "Ana uygulama başlatılamadı.", e);
			System.exit(2);
			return;
		}
		System.exit(0);
	}

	private static List<String> filterPassthroughArgs(String[] args, boolean hotRestart) {
		List<String> out = new ArrayList<>();
		int i = 0;
		if (hotRestart) {
			i = 1;
		}
		for (; i < args.length; i++) {
			out.add(args[i]);
		}
		return out;
	}

	private static String readLocalVersion(Path versionFile) {
		try {
			if (Files.isReadable(versionFile)) {
				return Files.readString(versionFile).trim();
			}
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("launcher", "version.txt okunamadı", e);
		}
		return "";
	}

	private static Path resolveLocalRoot() {
		String env = System.getenv("AQUAPARK_LOCAL_DIR");
		if (env != null && !env.isBlank()) {
			return Path.of(env.trim());
		}
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return Path.of("C:/Aquapark");
		}
		return Path.of(System.getProperty("user.home"), "Aquapark");
	}

	private static Path resolveUpdateRoot() {
		String env = System.getenv("AQUAPARK_UPDATE_ROOT");
		if (env != null && !env.isBlank()) {
			return Path.of(env.trim());
		}
		return Path.of("C:/Aquapark_Update");
	}

	private static String envOr(String key, String def) {
		String v = System.getenv(key);
		return v != null && !v.isBlank() ? v.trim() : def;
	}

	private static Path resolveJavaExecutable() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			Path home = Path.of(System.getProperty("java.home"));
			Path bin = home.resolve("bin").resolve(os.contains("win") ? "java.exe" : "java");
			if (Files.isExecutable(bin)) {
				return bin;
			}
		} catch (Exception ignored) {
		}
		return Path.of("java");
	}

	private static void sleepQuiet(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}

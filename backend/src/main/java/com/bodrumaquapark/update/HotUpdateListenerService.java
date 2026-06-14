package com.bodrumaquapark.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.bodrumaquapark.AppVersion;
import com.bodrumaquapark.config.AquaparkUpdateProperties;
import com.bodrumaquapark.repository.AppSettingRepository;
import com.bodrumaquapark.util.AquaparkDiagnosticLog;

import javafx.application.Platform;

/**
 * Çalışma anında {@code latest.txt} izleme; yeni sürümde masaüstü onayı ve başlatıcı ile yeniden başlatma.
 */
@Service
public class HotUpdateListenerService {

	private static final Logger log = LoggerFactory.getLogger(HotUpdateListenerService.class);

	private final AquaparkUpdateProperties props;
	private final AppSettingRepository appSettingRepository;
	private final DesktopHotUpdatePrompt desktopHotUpdatePrompt;
	private final ConfigurableApplicationContext applicationContext;

	public HotUpdateListenerService(AquaparkUpdateProperties props, AppSettingRepository appSettingRepository,
			DesktopHotUpdatePrompt desktopHotUpdatePrompt, ConfigurableApplicationContext applicationContext) {
		this.props = props;
		this.appSettingRepository = appSettingRepository;
		this.desktopHotUpdatePrompt = desktopHotUpdatePrompt;
		this.applicationContext = applicationContext;
	}

	@Scheduled(fixedDelayString = "${aquapark.update.check-interval-ms:900000}")
	public void pollRemoteLatest() {
		if (!props.isEnabled()) {
			return;
		}
		try {
			Path root = props.getNetworkRoot();
			if (root == null || root.toString().isBlank()) {
				return;
			}
			Path latestPath = root.resolve(props.getLatestFileName());
			if (!Files.isReadable(latestPath)) {
				return;
			}
			String remote = Files.readString(latestPath).trim();
			if (remote.isEmpty()) {
				return;
			}
			String current = readLocalPinnedVersion().orElse(AppVersion.VERSION);
			if (current.equals(remote)) {
				return;
			}

			boolean mandatory = isMandatory(remote);

			log.info("Hot-update: yeni sürüm algılandı remote={} current={} mandatory={}", remote, current, mandatory);
			desktopHotUpdatePrompt.prompt(current, remote, mandatory, () -> spawnLauncherAndExit(remote));
		} catch (Exception e) {
			try {
				AquaparkDiagnosticLog.append("update", "pollRemoteLatest", e);
			} catch (Exception ignored) {
			}
		}
	}

	private Optional<String> readLocalPinnedVersion() {
		try {
			Path dir = props.getLocalInstallDir();
			if (dir == null) {
				return Optional.empty();
			}
			Path vf = dir.resolve(props.getVersionFileName());
			if (Files.isReadable(vf)) {
				String v = Files.readString(vf).trim();
				if (!v.isEmpty()) {
					return Optional.of(v);
				}
			}
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("update", "readLocalPinnedVersion", e);
		}
		return Optional.empty();
	}

	private boolean isMandatory(String remote) {
		try {
			String key = props.getMandatoryUpdateSettingKey();
			if (key == null || key.isBlank()) {
				return false;
			}
			return appSettingRepository.findBySettingKey(key)
					.map(s -> remote.equals(trimOrEmpty(s.getSettingValue())))
					.orElse(false);
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("update", "isMandatory (AppSetting)", e);
			return false;
		}
	}

	private void spawnLauncherAndExit(String remote) {
		try {
			String launcher = trimOrEmpty(props.getLauncherJarPath());
			if (launcher.isEmpty()) {
				AquaparkDiagnosticLog.append("update",
						"Launcher JAR yolu boş (aquapark.update.launcher-jar-path); yeniden başlatma atlandı. remote="
								+ remote,
						null);
				return;
			}
			Path launcherPath = Path.of(launcher);
			if (!Files.isReadable(launcherPath)) {
				AquaparkDiagnosticLog.append("update", "Launcher okunamıyor: " + launcherPath, null);
				return;
			}
			Path javaExe = resolveJavaExecutable();
			java.util.List<String> cmd = new java.util.ArrayList<>();
			cmd.add(javaExe.toString());
			cmd.add("-jar");
			cmd.add(launcherPath.toAbsolutePath().toString());
			cmd.add("--hot-restart");
			String extra = trimOrEmpty(props.getChildJvmExtraArgs());
			if (!extra.isEmpty()) {
				for (String p : extra.split("\\s+")) {
					if (!p.isEmpty()) {
						cmd.add(p);
					}
				}
			}
			new ProcessBuilder(cmd).inheritIO().start();
			AquaparkDiagnosticLog.append("update", "Launcher tetiklendi; uygulama kapatılıyor. Hedef sürüm=" + remote,
					null);
			Thread.sleep(400);
			try {
				Platform.exit();
			} catch (Throwable ignored) {
			}
			int code = SpringApplication.exit(applicationContext, () -> 0);
			System.exit(code);
		} catch (Exception e) {
			AquaparkDiagnosticLog.append("update", "spawnLauncherAndExit", e);
		}
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

	private static String trimOrEmpty(String s) {
		return s != null ? s.trim() : "";
	}
}

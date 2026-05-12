package com.bodrumaquapark.config;

import java.nio.file.Path;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * Merkezi güncelleme kökü (örn. {@code C:/Aquapark_Update/latest.txt}) ve yerel kurulum dizini.
 */
@ConfigurationProperties(prefix = "aquapark.update")
public class AquaparkUpdateProperties {

	/**
	 * Kapalı: ağ kontrolü yapılmaz (CI / geliştirme).
	 */
	private boolean enabled = true;

	/**
	 * İstanbul sunucu klasörü kökü (içinde {@code latest.txt} ve {@code {sürüm}/app.jar}).
	 * Property boşsa ortam {@code AQUAPARK_UPDATE_ROOT}, o da yoksa {@code C:/Aquapark_Update}.
	 */
	private Path networkRoot;

	private String latestFileName = "latest.txt";
	private String remoteJarName = "app.jar";
	private String localJarName = "app.jar";
	private String versionFileName = "version.txt";

	/** Varsayılan 15 dakika */
	private long checkIntervalMs = 900_000L;

	/**
	 * Canlı güncelleme onayından sonra çalıştırılacak başlatıcı JAR (aynı Smart Launcher).
	 */
	private String launcherJarPath = "";

	/** Ana uygulamaya iletilecek ek argümanlar (örn. {@code --desktop}). */
	private String childJvmExtraArgs = "--desktop";

	/** {@link com.bodrumaquapark.entity.AppSetting} anahtarı; değer sunucudaki sürümle eşitse güncelleme zorunlu sayılır. */
	private String mandatoryUpdateSettingKey = "aquapark.mandatory_update_version";

	/**
	 * Boşsa OS'e göre varsayılan: Windows {@code C:/Aquapark}, aksi {@code ~/Aquapark}.
	 */
	private Path localInstallDir;

	@PostConstruct
	void applyDefaults() {
		try {
			if (localInstallDir == null || localInstallDir.toString().isBlank()) {
				String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
				if (os.contains("win")) {
					localInstallDir = Path.of("C:/Aquapark");
				} else {
					localInstallDir = Path.of(System.getProperty("user.home"), "Aquapark");
				}
			}
		} catch (Exception e) {
			localInstallDir = Path.of(System.getProperty("user.home"), "Aquapark");
		}
		if (networkRoot == null || networkRoot.toString().isBlank()) {
			String env = System.getenv("AQUAPARK_UPDATE_ROOT");
			if (env != null && !env.isBlank()) {
				networkRoot = Path.of(env.trim());
			} else {
				networkRoot = Path.of("C:/Aquapark_Update");
			}
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Path getNetworkRoot() {
		return networkRoot;
	}

	public void setNetworkRoot(Path networkRoot) {
		this.networkRoot = networkRoot;
	}

	public String getLatestFileName() {
		return latestFileName;
	}

	public void setLatestFileName(String latestFileName) {
		this.latestFileName = latestFileName;
	}

	public String getRemoteJarName() {
		return remoteJarName;
	}

	public void setRemoteJarName(String remoteJarName) {
		this.remoteJarName = remoteJarName;
	}

	public String getLocalJarName() {
		return localJarName;
	}

	public void setLocalJarName(String localJarName) {
		this.localJarName = localJarName;
	}

	public String getVersionFileName() {
		return versionFileName;
	}

	public void setVersionFileName(String versionFileName) {
		this.versionFileName = versionFileName;
	}

	public long getCheckIntervalMs() {
		return checkIntervalMs;
	}

	public void setCheckIntervalMs(long checkIntervalMs) {
		this.checkIntervalMs = checkIntervalMs;
	}

	public String getLauncherJarPath() {
		return launcherJarPath;
	}

	public void setLauncherJarPath(String launcherJarPath) {
		this.launcherJarPath = launcherJarPath;
	}

	public String getChildJvmExtraArgs() {
		return childJvmExtraArgs;
	}

	public void setChildJvmExtraArgs(String childJvmExtraArgs) {
		this.childJvmExtraArgs = childJvmExtraArgs;
	}

	public String getMandatoryUpdateSettingKey() {
		return mandatoryUpdateSettingKey;
	}

	public void setMandatoryUpdateSettingKey(String mandatoryUpdateSettingKey) {
		this.mandatoryUpdateSettingKey = mandatoryUpdateSettingKey;
	}

	public Path getLocalInstallDir() {
		return localInstallDir;
	}

	public void setLocalInstallDir(Path localInstallDir) {
		this.localInstallDir = localInstallDir;
	}
}

package com.bodrumaquapark.update;

import java.awt.GraphicsEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bodrumaquapark.util.AquaparkDiagnosticLog;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/**
 * JavaFX masaüstü (POS) için canlı güncelleme onay penceresi. Headless ortamda no-op.
 */
@Component
public class DesktopHotUpdatePrompt {

	private static final Logger log = LoggerFactory.getLogger(DesktopHotUpdatePrompt.class);

	private final java.util.concurrent.atomic.AtomicBoolean dialogOpen = new java.util.concurrent.atomic.AtomicBoolean();

	public void prompt(String currentVersion, String remoteVersion, boolean mandatory, Runnable onApproveRestart) {
		try {
			if (GraphicsEnvironment.isHeadless()) {
				log.info("Hot-update: headless; yeni sürüm {} (mevcut {})", remoteVersion, currentVersion);
				return;
			}
			if (!dialogOpen.compareAndSet(false, true)) {
				return;
			}
			Platform.runLater(() -> {
				try {
					AlertType type = mandatory ? AlertType.WARNING : AlertType.CONFIRMATION;
					Alert alert = new Alert(type);
					alert.setTitle("Güncelleme");
					alert.setHeaderText(null);
					alert.setContentText(
							"Yeni bir güncelleme mevcut, uygulama yeniden başlatılsın mı?"
									+ "\n\nSunucu (latest): " + remoteVersion + "\nBu cihaz: " + currentVersion);
					ButtonType yes = new ButtonType("Evet", ButtonBar.ButtonData.OK_DONE);
					ButtonType no = new ButtonType("Hayır", ButtonBar.ButtonData.CANCEL_CLOSE);
					if (mandatory) {
						alert.getButtonTypes().setAll(yes);
					} else {
						alert.getButtonTypes().setAll(yes, no);
					}
					alert.showAndWait().ifPresent(bt -> {
						if (bt == yes) {
							try {
								onApproveRestart.run();
							} catch (Exception ex) {
								AquaparkDiagnosticLog.append("update", "onApproveRestart", ex);
							}
						}
					});
				} catch (Exception e) {
					AquaparkDiagnosticLog.append("update", "JavaFX güncelleme diyaloğu", e);
				} finally {
					dialogOpen.set(false);
				}
			});
		} catch (Throwable t) {
			dialogOpen.set(false);
			AquaparkDiagnosticLog.append("update", "Hot-update prompt başlatılamadı", t);
		}
	}
}

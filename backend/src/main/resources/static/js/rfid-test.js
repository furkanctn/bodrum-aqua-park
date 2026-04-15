(function () {
	var TOKEN_KEY = "aqua_token";
	if (!sessionStorage.getItem(TOKEN_KEY)) {
		window.location.replace("/index.html");
		return;
	}

	var statusEl = document.getElementById("rfid-status");
	var logEl = document.getElementById("rfid-log");
	var baudEl = document.getElementById("rfid-baud");
	var dtrEl = document.getElementById("rfid-dtr");
	var rtsEl = document.getElementById("rfid-rts");
	var sendHexEl = document.getElementById("rfid-send-hex");
	var btnSendHex = document.getElementById("btn-rfid-send-hex");
	var btnBaudSweep = document.getElementById("btn-rfid-baud-sweep");
	var btnDtrPulse = document.getElementById("btn-rfid-dtr-pulse");
	var sweepLongEl = document.getElementById("rfid-sweep-long");
	var hidInput = document.getElementById("rfid-hid-input");
	var hidGlobalStateEl = document.getElementById("rfid-hid-global-state");
	var btnSerial = document.getElementById("btn-rfid-serial");
	var btnStop = document.getElementById("btn-rfid-stop");
	var btnClear = document.getElementById("btn-rfid-clear");
	var btnHidGlobal = document.getElementById("btn-rfid-hid-global");
	var btnHidGlobalStop = document.getElementById("btn-rfid-hid-global-stop");
	var btnFocusHid = document.getElementById("btn-rfid-focus-hid");

	var serialPort = null;
	var serialReader = null;
	var serialWriter = null;
	var serialListening = false;
	var baudSweepRunning = false;
	var serialBytesTotal = 0;
	var serialWatchdog = null;

	var hidGlobalOn = false;
	var hidGlobalHandler = null;
	var hidIdleTimer = null;
	/** Okuyucu tüm karakterleri gönderdikten sonra (Enter yoksa) taramayı kapatmak için ms */
	var HID_IDLE_MS = 150;
	/** Enter/Tab olmadan otomatik kapatırken en az bu kadar karakter (yanlışlıkla tek tuşu UID sayma) */
	var HID_IDLE_MIN_LEN = 4;

	function setStatus(msg) {
		if (statusEl) statusEl.textContent = msg;
	}

	function nowStr() {
		return new Date().toLocaleTimeString("tr-TR", { hour12: false });
	}

	function bytesToHex(u8) {
		if (!u8 || !u8.length) return "";
		var s = "";
		for (var i = 0; i < u8.length; i++) {
			s += u8[i].toString(16).padStart(2, "0") + " ";
		}
		return s.trim().toUpperCase();
	}

	function tryAscii(u8) {
		if (!u8 || u8.length === 0) return null;
		var s = "";
		for (var i = 0; i < u8.length; i++) {
			var b = u8[i];
			if (b >= 32 && b <= 126) s += String.fromCharCode(b);
			else return null;
		}
		return s;
	}

	function appendLog(line) {
		if (!logEl) return;
		logEl.textContent += line + "\n";
		logEl.scrollTop = logEl.scrollHeight;
	}

	function parseHexLine(str) {
		if (!str || !String(str).trim()) return null;
		var clean = String(str).replace(/[^0-9a-fA-F]/g, "");
		if (clean.length === 0 || clean.length % 2 !== 0) return null;
		var out = new Uint8Array(clean.length / 2);
		for (var i = 0; i < clean.length; i += 2) {
			out[i / 2] = parseInt(clean.substr(i, 2), 16);
		}
		return out;
	}

	function setSerialSendEnabled(on) {
		if (sendHexEl) sendHexEl.disabled = !on;
		if (btnSendHex) btnSendHex.disabled = !on;
		["btn-send-cr", "btn-send-lf", "btn-send-01"].forEach(function (id) {
			var el = document.getElementById(id);
			if (el) el.disabled = !on;
		});
	}

	function setSerialPortOpen(connected) {
		if (btnDtrPulse) btnDtrPulse.disabled = !connected;
	}

	function sleep(ms) {
		return new Promise(function (r) {
			setTimeout(r, ms);
		});
	}

	async function pulseDtrSignals(port, useDtr, useRts) {
		if (!port || !port.setSignals) return;
		try {
			await port.setSignals({ dataTerminalReady: false, requestToSend: false });
			await sleep(120);
			await port.setSignals({ dataTerminalReady: !!useDtr, requestToSend: !!useRts });
		} catch (e) {}
	}

	async function sendPortBytesOnce(port, u8) {
		if (!port || !port.writable || !u8 || !u8.length) return;
		var w = null;
		try {
			w = port.writable.getWriter();
			await w.write(u8);
		} catch (e) {
			appendLog("[Uyarı] Seri gönder: " + (e && e.message ? e.message : e));
		}
		if (w) {
			try {
				await w.close();
			} catch (e2) {}
		}
	}

	/** Test sayfasında yanlış tek portu otomatik seçmeyelim — her sefer kullanıcı seçsin */
	function pickSerialPort() {
		return navigator.serial.requestPort();
	}

	async function runBaudSweep() {
		if (!navigator.serial || baudSweepRunning) return;
		baudSweepRunning = true;
		if (btnBaudSweep) btnBaudSweep.disabled = true;
		var useDtr = !dtrEl || dtrEl.checked;
		var useRts = rtsEl && rtsEl.checked;
		var sweepMs = sweepLongEl && sweepLongEl.checked ? 6000 : 3500;
		appendLog(
			"=== Baud taraması (her adımda DTR nabız + CR 0D gönderilir) · " +
				sweepMs +
				" ms dinleme — port seçin ==="
		);
		var port;
		try {
			port = await navigator.serial.requestPort();
		} catch (e) {
			if (e && e.name === "NotFoundError") {
				appendLog("[İptal] Port seçilmedi.");
			} else {
				appendLog("[Hata] " + (e && e.message ? e.message : e));
			}
			baudSweepRunning = false;
			if (btnBaudSweep) btnBaudSweep.disabled = false;
			return;
		}
		var bauds = [115200, 9600, 57600, 38400, 19200, 128000, 230400, 4800, 2400, 1200];
		for (var bi = 0; bi < bauds.length; bi++) {
			var baud = bauds[bi];
			appendLog("--- " + baud + " baud · " + sweepMs + " ms — kartı okutun ---");
			var timer = null;
			try {
				await port.open({
					baudRate: baud,
					dataBits: 8,
					stopBits: 1,
					parity: "none",
					flowControl: "none",
				});
				if (port.setSignals) {
					await port.setSignals({ dataTerminalReady: useDtr, requestToSend: useRts }).catch(function () {});
				}
				await pulseDtrSignals(port, useDtr, useRts);
				await sendPortBytesOnce(port, new Uint8Array([0x0d]));
				await sleep(40);
				await sendPortBytesOnce(port, new Uint8Array([0x01]));
				var reader = port.readable.getReader();
				var acc = [];
				timer = setTimeout(function () {
					reader.cancel().catch(function () {});
				}, sweepMs);
				try {
					for (;;) {
						var r = await reader.read();
						if (r.done) break;
						if (r.value && r.value.byteLength) {
							var u = new Uint8Array(r.value);
							for (var i = 0; i < u.length; i++) acc.push(u[i]);
						}
					}
				} catch (readErr) {
					/* reader.cancel() */
				}
				if (timer) clearTimeout(timer);
				try {
					await reader.releaseLock();
				} catch (rl) {}
				await port.close();
				if (acc.length > 0) {
					appendLog(
						"[BULUNDU] " + baud + " baud → " + acc.length + " bayt: " + bytesToHex(new Uint8Array(acc))
					);
					appendLog("→ Baud kutusuna bu hızı yazıp «USB seri dinle» ile bağlanın.");
					if (baudEl) baudEl.value = String(baud);
					setStatus("Seri veri: " + baud + " baud");
					baudSweepRunning = false;
					if (btnBaudSweep) btnBaudSweep.disabled = false;
					return;
				}
				appendLog("[ ] " + baud + " baud: 0 bayt");
			} catch (e) {
				if (timer) clearTimeout(timer);
				appendLog("[Hata] " + baud + " baud: " + (e && e.message ? e.message : e));
				try {
					await port.close();
				} catch (e2) {}
			}
		}
		appendLog(
			"=== Tarama bitti: UART’ta hâlâ 0 bayt. Modül muhtemelen seri UID vermiyor (Wiegand/HID/özel protokol). ==="
		);
		appendLog("→ Üstte HID testi; Mac’te CoolTerm ile aynı portu deneyin; okuyucu etiketinde çıkış tipine bakın.");
		setStatus("Tarama bitti — seri sessiz");
		baudSweepRunning = false;
		if (btnBaudSweep) btnBaudSweep.disabled = false;
	}

	function clearSerialWatchdog() {
		if (serialWatchdog) {
			clearTimeout(serialWatchdog);
			serialWatchdog = null;
		}
	}

	function startSerialWatchdog() {
		clearSerialWatchdog();
		serialWatchdog = setTimeout(function () {
			if (serialListening && serialBytesTotal === 0) {
				appendLog(
					"[İpucu] Seri hattta hâlâ 0 bayt. Çoğu okuyucu HID (klavye) modundadır — sayfadaki «HID» bölümünü deneyin. Seri ise: baud 9600/115200, DTR kutusunu tersleyin, farklı USB portu."
				);
			}
		}, 22000);
	}

	async function stopSerial() {
		clearSerialWatchdog();
		serialListening = false;
		if (serialReader) {
			try {
				await serialReader.cancel();
			} catch (e) {}
			try {
				await serialReader.releaseLock();
			} catch (e) {}
			serialReader = null;
		}
		if (serialWriter) {
			try {
				await serialWriter.close();
			} catch (e) {}
			serialWriter = null;
		}
		if (serialPort) {
			try {
				await serialPort.close();
			} catch (e) {}
			serialPort = null;
		}
		setSerialSendEnabled(false);
		setSerialPortOpen(false);
		setStatus("Seri dinleme durduruldu.");
		if (btnSerial) btnSerial.disabled = false;
		if (btnStop) btnStop.disabled = true;
	}

	async function serialLoop() {
		while (serialListening && serialReader) {
			try {
				var r = await serialReader.read();
				if (r.done) break;
				if (!serialListening) break;
				var chunk = r.value instanceof Uint8Array ? r.value : new Uint8Array(r.value);
				if (chunk.length === 0) continue;
				serialBytesTotal += chunk.length;
				clearSerialWatchdog();
				startSerialWatchdog();
				setStatus(
					"Bağlı — gelen toplam " + serialBytesTotal + " bayt. Kart okutmaya devam edebilirsiniz."
				);
				var ascii = tryAscii(chunk);
				var line =
					"[" +
					nowStr() +
					"] " +
					chunk.length +
					" bayt | HEX: " +
					bytesToHex(chunk) +
					(ascii ? " | ASCII: " + JSON.stringify(ascii) : " | (binary)");
				appendLog(line);
			} catch (e) {
				if (serialListening) {
					appendLog("[Seri kesildi] " + (e && e.message ? e.message : e));
				}
				break;
			}
		}
	}

	function hidClearIdleTimer() {
		if (hidIdleTimer) {
			clearTimeout(hidIdleTimer);
			hidIdleTimer = null;
		}
	}

	function hidCommitScan(raw) {
		hidClearIdleTimer();
		var v = raw != null ? String(raw).trim() : "";
		if (!v.length) {
			return;
		}
		appendLog("[" + nowStr() + "] HID (klavye) → " + JSON.stringify(v));
		if (hidInput) {
			hidInput.value = "";
		}
	}

	function hidScheduleIdleFlush() {
		if (!hidGlobalOn || !hidInput) {
			return;
		}
		hidClearIdleTimer();
		hidIdleTimer = setTimeout(function () {
			hidIdleTimer = null;
			if (!hidGlobalOn || !hidInput) {
				return;
			}
			var v = hidInput.value.trim();
			if (v.length >= HID_IDLE_MIN_LEN) {
				hidCommitScan(hidInput.value);
			}
		}, HID_IDLE_MS);
	}

	/**
	 * Klavye kama (keyboard wedge): okuyucu USB klavye gibi yazıyor.
	 * Odak kutudayken doğal input; odak başka yerdeyken tuşları kutuya yönlendirir.
	 * Enter / Tab → tamamla; Enter göndermeyen okuyucular için kısa süre sessizlikte otomatik tamamlama.
	 */
	function hidKeyboardWedgeKeydown(e) {
		if (!hidGlobalOn) {
			return;
		}
		if (e.key === "Enter") {
			if (hidInput && document.activeElement === hidInput) {
				return;
			}
			if (hidInput) {
				e.preventDefault();
				hidCommitScan(hidInput.value);
			}
			return;
		}
		if (e.key === "Tab") {
			if (hidInput && hidInput.value.trim().length) {
				e.preventDefault();
				hidCommitScan(hidInput.value);
			}
			return;
		}
		if (hidInput && document.activeElement !== hidInput) {
			if (e.key.length === 1) {
				e.preventDefault();
				hidInput.value += e.key;
				hidInput.dispatchEvent(new Event("input", { bubbles: true }));
				hidInput.focus();
			}
		}
	}

	function setHidGlobal(on) {
		hidGlobalOn = on;
		if (hidGlobalStateEl) {
			hidGlobalStateEl.textContent = on
				? "Klavye dinleme AÇIK — kartı okutun (kutu odaklı). Enter/Tab veya " +
						HID_IDLE_MS +
						" ms sonra otomatik tamamlanır."
				: "";
		}
		if (btnHidGlobal) btnHidGlobal.disabled = on;
		if (btnHidGlobalStop) btnHidGlobalStop.disabled = !on;
		if (on) {
			hidClearIdleTimer();
			if (hidInput) {
				hidInput.value = "";
				hidInput.focus();
			}
			if (!hidGlobalHandler) {
				hidGlobalHandler = hidKeyboardWedgeKeydown;
				window.addEventListener("keydown", hidGlobalHandler, true);
			}
		} else {
			hidClearIdleTimer();
			if (hidGlobalHandler) {
				window.removeEventListener("keydown", hidGlobalHandler, true);
				hidGlobalHandler = null;
			}
		}
	}

	if (btnHidGlobal) {
		btnHidGlobal.addEventListener("click", function () {
			setHidGlobal(true);
			appendLog("--- Klavye (HID) dinleme açıldı — kartı okutun ---");
		});
	}
	if (btnHidGlobalStop) {
		btnHidGlobalStop.addEventListener("click", function () {
			setHidGlobal(false);
			appendLog("--- Klavye (HID) dinleme kapandı ---");
		});
	}

	if (btnSerial) {
		btnSerial.addEventListener("click", async function () {
			if (!navigator.serial) {
				setStatus("Web Serial yok — Chrome veya Edge kullanın.");
				appendLog("[Hata] navigator.serial tanımsız.");
				return;
			}
			var baud = parseInt((baudEl && baudEl.value) || "115200", 10);
			if (isNaN(baud) || baud < 300) baud = 115200;
			var useDtr = !dtrEl || dtrEl.checked;
			try {
				setStatus("Port seçimi…");
				serialPort = await pickSerialPort();
				var openOpts = {
					baudRate: baud,
					dataBits: 8,
					stopBits: 1,
					parity: "none",
					flowControl: "none",
					bufferSize: 65536,
				};
				await serialPort.open(openOpts);
				var useRts = rtsEl && rtsEl.checked;
				if (serialPort.setSignals) {
					try {
						await serialPort.setSignals({
							dataTerminalReady: useDtr,
							requestToSend: useRts,
						});
					} catch (sigErr) {
						appendLog("[Uyarı] setSignals: " + (sigErr && sigErr.message ? sigErr.message : sigErr));
					}
				}
				try {
					if (serialPort.getInfo) {
						appendLog("[Port bilgisi] " + JSON.stringify(serialPort.getInfo()));
					}
				} catch (e) {}
				serialReader = serialPort.readable.getReader();
				setSerialPortOpen(true);
				try {
					serialWriter = serialPort.writable.getWriter();
				} catch (wErr) {
					appendLog("[Uyarı] Seri yazma yok: " + (wErr && wErr.message ? wErr.message : wErr));
				}
				setSerialSendEnabled(!!serialWriter);
				serialListening = true;
				serialBytesTotal = 0;
				setStatus(
					"Seri bağlı (" +
						baud +
						" baud, DTR " +
						(useDtr ? "on" : "off") +
						", RTS " +
						(useRts ? "on" : "off") +
						") — kartı okutun veya hex gönderin."
				);
				btnSerial.disabled = true;
				if (btnStop) btnStop.disabled = false;
				appendLog(
					"--- Seri açıldı: " +
						baud +
						" 8N1, DTR=" +
						(useDtr ? "on" : "off") +
						", RTS=" +
						(useRts ? "on" : "off") +
						" ---"
				);
				startSerialWatchdog();
				serialLoop().catch(function (e) {
					appendLog("[Seri hata] " + (e && e.message ? e.message : e));
				});
			} catch (e) {
				if (e && e.name === "NotFoundError") {
					setStatus("Port seçilmedi.");
					appendLog("[İptal] Port seçilmedi.");
				} else {
					setStatus("Bağlantı hatası.");
					appendLog("[Hata] " + (e && e.message ? e.message : e));
				}
				await stopSerial();
			}
		});
	}

	if (btnStop) {
		btnStop.addEventListener("click", function () {
			stopSerial();
		});
	}

	if (btnClear) {
		btnClear.addEventListener("click", function () {
			if (logEl) logEl.textContent = "";
		});
	}

	if (btnBaudSweep) {
		btnBaudSweep.addEventListener("click", function () {
			runBaudSweep().catch(function (e) {
				appendLog("[Tarama] " + (e && e.message ? e.message : e));
				baudSweepRunning = false;
				btnBaudSweep.disabled = false;
			});
		});
	}

	if (btnSendHex && sendHexEl) {
		btnSendHex.addEventListener("click", async function () {
			if (!serialWriter) {
				appendLog("[Hata] Önce «USB seri dinle» ile port açın.");
				return;
			}
			var u8 = parseHexLine(sendHexEl.value);
			if (!u8 || !u8.length) {
				appendLog("[Hata] Geçerli hex girin (örn. 0D veya 01FF).");
				return;
			}
			try {
				await serialWriter.write(u8);
				appendLog("[Gönderildi] " + bytesToHex(u8));
			} catch (e) {
				appendLog("[Gönderim] " + (e && e.message ? e.message : e));
			}
		});
	}

	function wireSendPreset(id, hex) {
		var b = document.getElementById(id);
		if (!b) return;
		b.addEventListener("click", async function () {
			if (!serialWriter) {
				appendLog("[Hata] Önce seri dinleme açın.");
				return;
			}
			var u8 = parseHexLine(hex);
			if (!u8 || !u8.length) return;
			try {
				await serialWriter.write(u8);
				appendLog("[Gönderildi] " + bytesToHex(u8));
			} catch (e) {
				appendLog("[Gönderim] " + (e && e.message ? e.message : e));
			}
		});
	}
	wireSendPreset("btn-send-cr", "0D");
	wireSendPreset("btn-send-lf", "0A");
	wireSendPreset("btn-send-01", "01");

	if (btnDtrPulse) {
		btnDtrPulse.addEventListener("click", async function () {
			if (!serialPort || !serialPort.setSignals) {
				appendLog("[Hata] Önce «USB seri dinle» ile port açın.");
				return;
			}
			var useDtr = !dtrEl || dtrEl.checked;
			var useRts = rtsEl && rtsEl.checked;
			await pulseDtrSignals(serialPort, useDtr, useRts);
			appendLog("[DTR] Nabız tamam — ardından kartı okutun.");
		});
	}

	function wireBaud(id, val) {
		var b = document.getElementById(id);
		if (b && baudEl) {
			b.addEventListener("click", function () {
				baudEl.value = String(val);
			});
		}
	}
	wireBaud("baud-9600", 9600);
	wireBaud("baud-115200", 115200);
	wireBaud("baud-57600", 57600);

	if (hidInput) {
		hidInput.addEventListener("input", function () {
			if (hidGlobalOn) {
				hidScheduleIdleFlush();
			}
		});
		hidInput.addEventListener("keydown", function (e) {
			if (e.key === "Enter") {
				e.preventDefault();
				hidCommitScan(hidInput.value);
			}
		});
	}

	if (btnFocusHid && hidInput) {
		btnFocusHid.addEventListener("click", function () {
			hidInput.focus();
			setStatus("Klavye (HID) kutusu odaklandı — kartı okutun.");
		});
	}

	setStatus(
		navigator.serial
			? "Önce üstteki HID testini deneyin; çoğu okuyucu seri göstermez."
			: "Web Serial yok — sadece HID testi."
	);
})();

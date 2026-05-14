#!/usr/bin/env python3
"""
Raspberry Pi örneği: kart UID okunur, sunucuya POST /api/access/check atılır;
allowed=true ise GPIO 17 röle (pinctrl) ile kısa süre aktif edilir.

Gereksinimler:
  - Ortam: API_BASE (örn. http://192.168.1.10:8081), DEVICE_ID, DEVICE_TOKEN
  - Kart okuyucu: bu örnek stdin satırı okur; evdev ile HID entegrasyonunu kendi donanımınıza göre ekleyin.

Örnek:
  export API_BASE=http://127.0.0.1:8081
  export DEVICE_ID=TURNSTILE-RPI-1
  export DEVICE_TOKEN=gizli-token
  echo 04AABBCCDD | python3 access_check_reader.py
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request


def post_check(api_base: str, card_id: str, device_id: str, device_token: str) -> dict:
	url = api_base.rstrip("/") + "/api/access/check"
	body = json.dumps({"cardId": card_id, "deviceId": device_id}).encode("utf-8")
	req = urllib.request.Request(
		url,
		data=body,
		method="POST",
		headers={
			"Content-Type": "application/json",
			"Accept": "application/json",
			"X-DEVICE-TOKEN": device_token,
		},
	)
	with urllib.request.urlopen(req, timeout=10) as resp:
		return json.loads(resp.read().decode("utf-8"))


def trigger_relay_gpio17() -> None:
	# Raspberry Pi OS Bookworm: pinctrl (GPIO17 çıkış, aktif düşük tipik röle sürücü)
	subprocess.run(["pinctrl", "set", "17", "op", "dl"], check=False)
	time.sleep(3)
	subprocess.run(["pinctrl", "set", "17", "op", "dh"], check=False)


def main() -> int:
	api_base = os.environ.get("API_BASE", "http://127.0.0.1:8081")
	device_id = os.environ.get("DEVICE_ID", "")
	device_token = os.environ.get("DEVICE_TOKEN", "")
	if not device_id or not device_token:
		print("DEVICE_ID ve DEVICE_TOKEN ortam değişkenleri gerekli", file=sys.stderr)
		return 2

	if len(sys.argv) > 1:
		card_id = " ".join(sys.argv[1:]).strip()
	else:
		card_id = sys.stdin.readline().strip()
	if not card_id:
		print("Kart ID boş", file=sys.stderr)
		return 2

	try:
		data = post_check(api_base, card_id, device_id, device_token)
	except urllib.error.HTTPError as e:
		print("HTTP", e.code, e.read().decode("utf-8", errors="replace"), file=sys.stderr)
		return 1
	except urllib.error.URLError as e:
		print("Bağlantı hatası:", e, file=sys.stderr)
		return 1

	allowed = bool(data.get("allowed"))
	msg = data.get("message", "")
	print(json.dumps(data, ensure_ascii=False))
	if allowed:
		trigger_relay_gpio17()
	return 0 if allowed else 3


if __name__ == "__main__":
	sys.exit(main())

# Bodrum Aqua Park — Masaüstü kısayolu (simge ile).
# Kullanım: Bu dosyayı BodrumAquaPark.bat ile AYNI klasörde tutun; sağ tık > PowerShell ile çalıştır
# veya: powershell -ExecutionPolicy Bypass -File "Olustur-Masaustu-Kisayolu.ps1"
#
# Kısayol cmd.exe üzerinden minimize başlar; Edge --app penceresi öne çıkar.
# Tam ekran Edge için BodrumAquaPark.bat içinde (JAR satırından önce): set POS_EDGE_FULLSCREEN=1

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $here) {
	$here = (Get-Location).Path
}
$bat = Join-Path $here "BodrumAquaPark.bat"
if (-not (Test-Path -LiteralPath $bat)) {
	Write-Host "HATA: BodrumAquaPark.bat bulunamadi: $here" -ForegroundColor Red
	exit 1
}

$ico = Join-Path $here "app.ico"
$desk = [Environment]::GetFolderPath("Desktop")
$lnkPath = Join-Path $desk "Bodrum Aqua Park POS.lnk"

$cmdExe = Join-Path $env:SystemRoot "System32\cmd.exe"
if (-not (Test-Path -LiteralPath $cmdExe)) {
	$cmdExe = "C:\Windows\System32\cmd.exe"
}

$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut($lnkPath)
$sc.TargetPath = $cmdExe
$sc.Arguments = "/c `"$bat`""
$sc.WorkingDirectory = $here
$sc.Description = "Bodrum Aqua Park POS"
$sc.WindowStyle = 7
if (Test-Path -LiteralPath $ico) {
	$sc.IconLocation = "$ico,0"
} else {
	Write-Host "BILGI: app.ico yok; Windows varsayilan simge kullanilir. Isterseniz bu klasore app.ico ekleyip bu scripti tekrar calistirin." -ForegroundColor Yellow
}
$sc.Save()
Write-Host "Tamam: $lnkPath" -ForegroundColor Green

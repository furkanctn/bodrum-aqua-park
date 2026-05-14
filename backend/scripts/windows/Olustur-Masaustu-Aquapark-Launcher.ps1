# Bodrum Aqua Park — Masaüstü kısayolu (tek tıkla POS).
# Aquapark-Launcher-Startup.bat ile AYNI klasörde tutun.
#
# Çalıştırma:
#   Sağ tık > PowerShell ile çalıştır
#   veya: powershell -ExecutionPolicy Bypass -File "Olustur-Masaustu-Aquapark-Launcher.ps1"

$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $here) {
	$here = (Get-Location).Path
}
$bat = Join-Path $here "Aquapark-Launcher-Startup.bat"
if (-not (Test-Path -LiteralPath $bat)) {
	Write-Host "HATA: Aquapark-Launcher-Startup.bat bulunamadi: $here" -ForegroundColor Red
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
$sc.Description = "Bodrum Aqua Park — Launcher + POS"
$sc.WindowStyle = 7
if (Test-Path -LiteralPath $ico) {
	$sc.IconLocation = "$ico,0"
} else {
	Write-Host "BILGI: app.ico yok; varsayilan simge kullanilir." -ForegroundColor Yellow
}
$sc.Save()
Write-Host "Tamam: $lnkPath" -ForegroundColor Green
Write-Host "Masaustunde 'Bodrum Aqua Park POS' kisayoluna tiklayin." -ForegroundColor Cyan

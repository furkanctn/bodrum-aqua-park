# Bodrum Aqua Park — Ilk kurulum / tam guncelleme (Yonetici PowerShell)
#
# Calistirma:
#   Set-ExecutionPolicy -Scope Process Bypass
#   .\AquaparkIlkKurulum.ps1
#
# Ne yapar:
#   1) FTP sunucusundaki Aquapark_Update/latest.txt'den guncel surumu okur.
#   2) O surume ait JAR + launcher JAR + BAT + config ornegi + yardimci scriptleri
#      C:\Aquapark altina indirir (config\application.properties'e DOKUNMAZ).
#   3) AquaparkGuncellemeCek.bat'a bu FTP bilgilerini yazar.
#   4) Otomatik guncelleme gorevini (Gorev Zamanlayici) kurar.
#   5) BodrumAquaPark.bat'i baslatir.
#
# Tek dosya olarak USB ile yeni POS'a tasinabilir; geri kalan her sey FTP'den gelir.
# Var olan kurulumda tekrar calistirilirsa BAT/launcher/config-ornegi gibi
# "altyapi" dosyalarini en guncel surume tazeler (config\application.properties sabit kalir).

$ErrorActionPreference = 'Stop'

# ---------- Ayarlar (kurulumda duzenleyin) ----------
$FtpHost      = '10.100.172.145'
$FtpPort      = 21
$FtpUser      = 'client'
$FtpPass      = '1154'
$FtpRemoteDir = ''

$InstallDir = 'C:\Aquapark'
# -----------------------------------------------------

$IsAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $IsAdmin) {
	Write-Error "Bu scripti Yonetici olarak calistirin (sag tik > PowerShell'i Yonetici olarak calistir)."
}

function Get-FtpFile {
	param([string]$RemotePath, [string]$LocalPath)
	$uri = "ftp://${FtpHost}:${FtpPort}${FtpRemoteDir}$RemotePath"
	$dir = Split-Path -Parent $LocalPath
	if ($dir -and -not (Test-Path -LiteralPath $dir)) {
		New-Item -ItemType Directory -Path $dir -Force | Out-Null
	}
	$client = New-Object System.Net.WebClient
	try {
		$client.Credentials = New-Object System.Net.NetworkCredential($FtpUser, $FtpPass)
		$client.DownloadFile($uri, $LocalPath)
	} finally {
		$client.Dispose()
	}
}

New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null

Write-Host "Surum bilgisi aliniyor (latest.txt)..."
$latestPath = Join-Path $InstallDir 'latest.txt'
Get-FtpFile -RemotePath '/latest.txt' -LocalPath $latestPath
$Version = (Get-Content -LiteralPath $latestPath -Raw).Trim()
Write-Host "Surum: $Version"

$Files = @(
	"bodrum-aqua-park-api-$Version.jar",
	"bodrum-aqua-park-api-$Version-launcher.jar",
	"BodrumAquaPark.bat",
	"AquaparkGuncellemeCek.bat",
	"AquaparkGuncellemeCek-Zamanla.ps1",
	"Olustur-Masaustu-Kisayolu.ps1",
	"javafx-logging.properties",
	"POS-USB-KURULUM.txt",
	"config/application.properties.example"
)

Write-Host "Dosyalar indiriliyor -> $InstallDir"
foreach ($f in $Files) {
	Write-Host "  - $f"
	Get-FtpFile -RemotePath "/$Version/$f" -LocalPath (Join-Path $InstallDir $f)
}

Write-Host ""
Write-Host "AquaparkGuncellemeCek.bat icine FTP bilgileri yaziliyor..."
$updaterPath = Join-Path $InstallDir 'AquaparkGuncellemeCek.bat'
(Get-Content -LiteralPath $updaterPath) |
	ForEach-Object {
		$_ -replace '^set "FTP_HOST=.*"', "set ""FTP_HOST=$FtpHost""" `
		   -replace '^set "FTP_PORT=.*"', "set ""FTP_PORT=$FtpPort""" `
		   -replace '^set "FTP_USER=.*"', "set ""FTP_USER=$FtpUser""" `
		   -replace '^set "FTP_PASS=.*"', "set ""FTP_PASS=$FtpPass""" `
		   -replace '^set "FTP_REMOTE_DIR=.*"', "set ""FTP_REMOTE_DIR=$FtpRemoteDir"""
	} | Set-Content -LiteralPath $updaterPath

Write-Host ""
Write-Host "Otomatik guncelleme kontrol gorevi kuruluyor (Gorev Zamanlayici)..."
& (Join-Path $InstallDir 'AquaparkGuncellemeCek-Zamanla.ps1')

Write-Host ""
Write-Host "==> Kurulum tamamlandi: $InstallDir (surum $Version)"
Write-Host "Uygulama baslatiliyor..."
Start-Process -FilePath (Join-Path $InstallDir 'BodrumAquaPark.bat') -WorkingDirectory $InstallDir

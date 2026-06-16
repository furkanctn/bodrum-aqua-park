# Bodrum Aqua Park — Gorev Zamanlayici: FTP'den guncelleme kontrolu (her 10 dakikada)
# Yonetici PowerShell:  Set-ExecutionPolicy -Scope Process Bypass
#                       .\AquaparkGuncellemeCek-Zamanla.ps1
#
# Onkosul: AquaparkGuncellemeCek.bat icindeki FTP_HOST / FTP_USER / FTP_PASS / FTP_REMOTE_DIR
# alanlarini gercek sunucu bilgileriyle doldurun.

$ErrorActionPreference = 'Stop'

$TaskName = 'BodrumAquaPark-GuncellemeKontrol'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BatPath = Join-Path $ScriptDir 'AquaparkGuncellemeCek.bat'

if (-not (Test-Path -LiteralPath $BatPath)) {
	Write-Error "AquaparkGuncellemeCek.bat bulunamadi: $BatPath"
}

$Existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($Existing) {
	Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
	Write-Host "Eski gorev kaldirildi: $TaskName"
}

$Action = New-ScheduledTaskAction -Execute $BatPath -WorkingDirectory $ScriptDir
$Trigger = New-ScheduledTaskTrigger -Once -At (Get-Date) -RepetitionInterval (New-TimeSpan -Minutes 10) -RepetitionDuration ([TimeSpan]::MaxValue)
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask `
	-TaskName $TaskName `
	-Action $Action `
	-Trigger $Trigger `
	-Settings $Settings `
	-Description 'Bodrum Aqua Park - FTP sunucudan guncelleme kontrolu (10 dakikada bir)' `
	-RunLevel Highest `
	-User 'SYSTEM'

Write-Host ""
Write-Host "Gorev olusturuldu: $TaskName"
Write-Host "  Calistirma: her 10 dakikada bir"
Write-Host "  Script:     $BatPath"
Write-Host ""
Write-Host "Test icin:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Kontrol:    Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host "Log:        C:\Aquapark_Update\guncelleme-kontrol.log"

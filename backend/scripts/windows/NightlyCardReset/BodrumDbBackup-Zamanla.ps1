# Bodrum Aqua Park — Windows Gorev Zamanlayici ile gunluk DB yedek
# Yonetici PowerShell:  Set-ExecutionPolicy -Scope Process Bypass
#                       .\BodrumDbBackup-Zamanla.ps1
#
# Yedek klasoru: C:\BodrumAquaPark\backup
# Saat: her gun 22:45 (kart sifirlama 23:00'ten once)

$ErrorActionPreference = 'Stop'

$TaskName = 'BodrumAquaPark-DbBackup'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BatPath = Join-Path $ScriptDir 'BodrumDbBackup.bat'

if (-not (Test-Path -LiteralPath $BatPath)) {
	Write-Error "BodrumDbBackup.bat bulunamadi: $BatPath"
}

$Existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($Existing) {
	Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
	Write-Host "Eski gorev kaldirildi: $TaskName"
}

$Action = New-ScheduledTaskAction -Execute $BatPath -WorkingDirectory $ScriptDir
$Trigger = New-ScheduledTaskTrigger -Daily -At '22:45'
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask `
	-TaskName $TaskName `
	-Action $Action `
	-Trigger $Trigger `
	-Settings $Settings `
	-Description 'Bodrum Aqua Park PostgreSQL gunluk yedek — her gun 22:45 → C:\BodrumAquaPark\backup' `
	-RunLevel Highest `
	-User 'SYSTEM'

Write-Host ""
Write-Host "Gorev olusturuldu: $TaskName"
Write-Host "  Calistirma: her gun 22:45"
Write-Host "  Script:     $BatPath"
Write-Host "  Yedek:      C:\BodrumAquaPark\backup\bodrum_aqua_park_*.backup"
Write-Host ""
Write-Host "Test icin:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Kontrol:    Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host "Log:        C:\BodrumAquaPark\backup\bodrum-db-backup.log"

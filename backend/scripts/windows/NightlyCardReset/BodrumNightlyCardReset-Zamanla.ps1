# Bodrum Aqua Park — Windows Gorev Zamanlayici ile gece 23:00 kart sifirlama
# Yonetici PowerShell:  Set-ExecutionPolicy -Scope Process Bypass
#                       .\BodrumNightlyCardReset-Zamanla.ps1
#
# Once pgAdmin'de postgresql-nightly-card-reset.sql dosyasini calistirin.

$ErrorActionPreference = 'Stop'

$TaskName = 'BodrumAquaPark-NightlyCardReset'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BatPath = Join-Path $ScriptDir 'BodrumNightlyCardReset.bat'

if (-not (Test-Path -LiteralPath $BatPath)) {
	Write-Error "BodrumNightlyCardReset.bat bulunamadi: $BatPath"
}

$Existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($Existing) {
	Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
	Write-Host "Eski gorev kaldirildi: $TaskName"
}

$Action = New-ScheduledTaskAction -Execute $BatPath -WorkingDirectory $ScriptDir
$Trigger = New-ScheduledTaskTrigger -Daily -At '23:00'
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask `
	-TaskName $TaskName `
	-Action $Action `
	-Trigger $Trigger `
	-Settings $Settings `
	-Description 'Bodrum Aqua Park gece kart sifirlama — her gun 23:00' `
	-RunLevel Highest `
	-User 'SYSTEM'

Write-Host ""
Write-Host "Gorev olusturuldu: $TaskName"
Write-Host "  Calistirma: her gun 23:00"
Write-Host "  Script:     $BatPath"
Write-Host ""
Write-Host "Test icin:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Kontrol:    Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host "Log:        C:\Backups\bodrum-aqua-park\bodrum-nightly-card-reset.log"

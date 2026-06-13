# Bodrum Aqua Park — Windows Gorev Zamanlayici ile otomatik DB yedek (15 gunde bir)
# Yonetici PowerShell:  Set-ExecutionPolicy -Scope Process Bypass
#                       .\BodrumDbBackup-Zamanla.ps1

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
$Trigger1 = New-ScheduledTaskTrigger -Monthly -DaysOfMonth 1 -At '03:00'
$Trigger2 = New-ScheduledTaskTrigger -Monthly -DaysOfMonth 15 -At '03:00'
$Settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable

Register-ScheduledTask `
	-TaskName $TaskName `
	-Action $Action `
	-Trigger @($Trigger1, $Trigger2) `
	-Settings $Settings `
	-Description 'Bodrum Aqua Park PostgreSQL yedek — ayin 1 ve 15, saat 03:00' `
	-RunLevel Highest `
	-User 'SYSTEM'

Write-Host ""
Write-Host "Gorev olusturuldu: $TaskName"
Write-Host "  Calistirma: ayin 1'i ve 15'i, 03:00"
Write-Host "  Script:     $BatPath"
Write-Host ""
Write-Host "Test icin:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Kontrol:    Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"

# Bodrum Aqua Park — her 30 dakikada bir rolling DB yedek
# Yonetici PowerShell:  Set-ExecutionPolicy -Scope Process Bypass
#                       .\BodrumDbRollingBackup-Zamanla.ps1
#
# Tek dosya (uzerine yazar): C:\BodrumAquaPark\backup\bodrum_aqua_park_rolling.backup
# Amac: en fazla ~30 dk onceye donebilmek (kart / ledger dahil tum DB)

$ErrorActionPreference = 'Stop'

$TaskName = 'BodrumAquaPark-DbRollingBackup'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BatPath = Join-Path $ScriptDir 'BodrumDbRollingBackup.bat'

if (-not (Test-Path -LiteralPath $BatPath)) {
	Write-Error "BodrumDbRollingBackup.bat bulunamadi: $BatPath"
}

$Existing = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($Existing) {
	Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
	Write-Host "Eski gorev kaldirildi: $TaskName"
}

$Action = New-ScheduledTaskAction -Execute $BatPath -WorkingDirectory $ScriptDir
# ~1 dk sonra baslar, uzun sure her 30 dakikada tekrarlar
$Trigger = New-ScheduledTaskTrigger -Once -At ((Get-Date).AddMinutes(1)) `
	-RepetitionInterval (New-TimeSpan -Minutes 30) `
	-RepetitionDuration (New-TimeSpan -Days 3650)
$Settings = New-ScheduledTaskSettingsSet `
	-AllowStartIfOnBatteries `
	-DontStopIfGoingOnBatteries `
	-StartWhenAvailable `
	-MultipleInstances IgnoreNew

Register-ScheduledTask `
	-TaskName $TaskName `
	-Action $Action `
	-Trigger $Trigger `
	-Settings $Settings `
	-Description 'Bodrum Aqua Park PostgreSQL rolling yedek — her 30 dk ayni dosyaya (bodrum_aqua_park_rolling.backup)' `
	-RunLevel Highest `
	-User 'SYSTEM'

Write-Host ""
Write-Host "Gorev olusturuldu: $TaskName"
Write-Host "  Calistirma: her 30 dakika"
Write-Host "  Script:     $BatPath"
Write-Host "  Yedek:      C:\BodrumAquaPark\backup\bodrum_aqua_park_rolling.backup"
Write-Host "  Log:        C:\BodrumAquaPark\backup\bodrum-db-rolling-backup.log"
Write-Host ""
Write-Host "Hemen test:  Start-ScheduledTask -TaskName '$TaskName'"
Write-Host "Kontrol:     Get-ScheduledTask -TaskName '$TaskName' | Get-ScheduledTaskInfo"
Write-Host ""
Write-Host "Geri yukleme (POS durdurun, ornek):"
Write-Host '  pg_restore -h 127.0.0.1 -p 5433 -U postgres -d bodrum_aqua_park --clean --if-exists C:\BodrumAquaPark\backup\bodrum_aqua_park_rolling.backup'

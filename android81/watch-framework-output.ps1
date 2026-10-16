$Host.UI.RawUI.WindowTitle = 'Nokia 6300 - Android 8.1 - output LIVE'
Write-Host 'Compilare Android 8.1 / Nokia 6300 - jurnal LIVE' -ForegroundColor Cyan
Write-Host 'Aceasta fereastra doar afiseaza jurnalul. Inchiderea ei nu opreste compilarea.' -ForegroundColor Yellow
Get-Content -LiteralPath '\\wsl.localhost\kaios\root\android81-leo\logs\framework-build.log' -Tail 25 -Wait
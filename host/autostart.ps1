# TaleSpark host: start with Windows (at logon, in the user session, so Chromium reaches the GPU).
#   powershell -ExecutionPolicy Bypass -File host\autostart.ps1          -> adds a shortcut to the Startup folder
#   powershell -ExecutionPolicy Bypass -File host\autostart.ps1 -Remove  -> removes it
# A Startup-folder shortcut (not a service): the host must run in the logged-in session, a service gets no GPU.
param([switch]$Remove)
$lnk = Join-Path ([Environment]::GetFolderPath('Startup')) 'TaleSpark Host.lnk'
if ($Remove) { Remove-Item $lnk -ErrorAction SilentlyContinue; Write-Output "removed: $lnk"; exit }
$root = Split-Path -Parent $PSScriptRoot
$py = (Get-Command py -ErrorAction Stop).Source
$w = New-Object -ComObject WScript.Shell; $s = $w.CreateShortcut($lnk)
$s.TargetPath = $py; $s.Arguments = "-3 `"$root\host\host.py`""; $s.WorkingDirectory = $root; $s.WindowStyle = 7   # minimised
$s.Description = 'TaleSpark host (مَدار): the phone makes films on this PC'; $s.Save()
Write-Output "added: $lnk"

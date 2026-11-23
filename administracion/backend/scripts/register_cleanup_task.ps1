# HU75: registra en el Programador de tareas de Windows la limpieza diaria de notificaciones (03:00).
# Ejecutar una vez en PowerShell desde administracion/backend:  .\scripts\register_cleanup_task.ps1
# Cada ejecución agrega su resultado a logs\cleanup_notifications.log.
param([string]$Hora = "03:00", [string]$Nombre = "PalaceTec - Limpieza de notificaciones")

$backend = Split-Path -Parent $PSScriptRoot
$python = Join-Path $backend ".venv\Scripts\python.exe"
$logs = Join-Path $backend "logs"
New-Item -ItemType Directory -Force $logs | Out-Null
$comando = "cd /d `"$backend`" && `"$python`" manage.py cleanup_notifications >> `"$logs\cleanup_notifications.log`" 2>&1"

schtasks /Create /F /SC DAILY /ST $Hora /TN $Nombre /TR "cmd /c $comando"
Write-Output "Tarea '$Nombre' programada todos los días a las $Hora. Log: $logs\cleanup_notifications.log"

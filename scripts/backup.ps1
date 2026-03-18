#Requires -Version 5.1
<#
.SYNOPSIS
    SQUID Database Backup Script
.DESCRIPTION
    Automated backup for PostgreSQL (pg_dump) and MySQL (mysqldump).
    Supports full and incremental (WAL-based for PG) backups.
.PARAMETER DbType
    Database type: 'postgresql' or 'mysql'
.PARAMETER Host
    Database host (default: localhost)
.PARAMETER Port
    Database port (default: 5432 for PG, 3306 for MySQL)
.PARAMETER Database
    Database name (default: squiddb)
.PARAMETER User
    Database user (default: squid)
.PARAMETER BackupDir
    Backup output directory (default: ./backups)
.PARAMETER Incremental
    If set, performs incremental backup (PG WAL archive / MySQL binlog)
.EXAMPLE
    .\backup.ps1 -DbType postgresql
    .\backup.ps1 -DbType mysql -Host 192.168.1.10 -Incremental
#>
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('postgresql','mysql')]
    [string]$DbType,

    [string]$Host = 'localhost',
    [int]$Port = 0,
    [string]$Database = 'squiddb',
    [string]$User = 'squid',
    [string]$BackupDir = (Join-Path $PSScriptRoot '..' 'backups'),
    [switch]$Incremental
)

$ErrorActionPreference = 'Stop'
$timestamp = Get-Date -Format 'yyyyMMdd_HHmmss'

# Resolve default port
if ($Port -eq 0) {
    $Port = if ($DbType -eq 'postgresql') { 5432 } else { 3306 }
}

# Ensure backup directory exists
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

Write-Host "============================================" -ForegroundColor Cyan
Write-Host " SQUID Database Backup" -ForegroundColor Cyan
Write-Host " Type : $DbType" -ForegroundColor Cyan
Write-Host " Host : ${Host}:${Port}" -ForegroundColor Cyan
Write-Host " DB   : $Database" -ForegroundColor Cyan
Write-Host " Mode : $(if ($Incremental) {'INCREMENTAL'} else {'FULL'})" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

if ($DbType -eq 'postgresql') {
    # ── PostgreSQL backup ──
    $dumpFile = Join-Path $BackupDir "squid_pg_${timestamp}.sql.gz"

    if ($Incremental) {
        # WAL-based incremental: use pg_basebackup for PITR
        $baseDir = Join-Path $BackupDir "pg_base_${timestamp}"
        Write-Host "Running pg_basebackup (incremental/PITR)..." -ForegroundColor Yellow
        & pg_basebackup -h $Host -p $Port -U $User -D $baseDir -Ft -z -Xs -P
        if ($LASTEXITCODE -ne 0) {
            Write-Error "pg_basebackup failed with exit code $LASTEXITCODE"
        }
        Write-Host "Base backup saved to: $baseDir" -ForegroundColor Green
    }
    else {
        Write-Host "Running pg_dump (full)..." -ForegroundColor Yellow
        # Full logical dump, compressed
        $rawFile = Join-Path $BackupDir "squid_pg_${timestamp}.sql"
        & pg_dump -h $Host -p $Port -U $User -d $Database -F p -f $rawFile
        if ($LASTEXITCODE -ne 0) {
            Write-Error "pg_dump failed with exit code $LASTEXITCODE"
        }
        # Compress
        if (Get-Command gzip -ErrorAction SilentlyContinue) {
            & gzip $rawFile
            Write-Host "Backup saved to: ${rawFile}.gz" -ForegroundColor Green
        }
        else {
            Compress-Archive -Path $rawFile -DestinationPath "${rawFile}.zip" -Force
            Remove-Item $rawFile -Force
            Write-Host "Backup saved to: ${rawFile}.zip" -ForegroundColor Green
        }
    }
}
else {
    # ── MySQL backup ──
    $dumpFile = Join-Path $BackupDir "squid_mysql_${timestamp}.sql"

    if ($Incremental) {
        # Flush logs and copy binary logs for PITR
        Write-Host "Running mysqldump with --flush-logs (incremental)..." -ForegroundColor Yellow
        & mysqldump -h $Host -P $Port -u $User -p --single-transaction --flush-logs --master-data=2 $Database > $dumpFile
    }
    else {
        Write-Host "Running mysqldump (full)..." -ForegroundColor Yellow
        & mysqldump -h $Host -P $Port -u $User -p --single-transaction --routines --triggers $Database > $dumpFile
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Error "mysqldump failed with exit code $LASTEXITCODE"
    }

    # Compress
    if (Get-Command gzip -ErrorAction SilentlyContinue) {
        & gzip $dumpFile
        Write-Host "Backup saved to: ${dumpFile}.gz" -ForegroundColor Green
    }
    else {
        Compress-Archive -Path $dumpFile -DestinationPath "${dumpFile}.zip" -Force
        Remove-Item $dumpFile -Force
        Write-Host "Backup saved to: ${dumpFile}.zip" -ForegroundColor Green
    }
}

# ── Cleanup old backups (keep last 30 days) ──
$cutoff = (Get-Date).AddDays(-30)
Get-ChildItem -Path $BackupDir -File | Where-Object { $_.LastWriteTime -lt $cutoff } | ForEach-Object {
    Write-Host "Removing old backup: $($_.Name)" -ForegroundColor DarkGray
    Remove-Item $_.FullName -Force
}

Write-Host ""
Write-Host "Backup completed at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Green

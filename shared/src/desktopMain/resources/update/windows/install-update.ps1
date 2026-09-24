param(
    [Parameter(Mandatory = $true)][int]$AppProcessId,
    [Parameter(Mandatory = $true)][string]$InstallerPath,
    [Parameter(Mandatory = $true)][string]$InstallDirectory,
    [Parameter(Mandatory = $true)][string]$PassInstallDirectory,
    [Parameter(Mandatory = $true)][string]$StagingDirectory,
    [Parameter(Mandatory = $true)][string]$LaunchAfterInstall
)

function Write-Log([string]$Message) {
    Write-Output "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
}

Write-Log "Waiting for Emberr to close"
Wait-Process -Id $AppProcessId -Timeout 60 -ErrorAction SilentlyContinue
if (Get-Process -Id $AppProcessId -ErrorAction SilentlyContinue) {
    Write-Log "Emberr was still running after a minute, so nothing was changed."
    exit 1
}

$installerArguments = @()
if ($LaunchAfterInstall -eq 'yes') {
    $installerArguments += '/passive'
} else {
    $installerArguments += '/quiet'
}
$installerArguments += '/norestart'
if ($PassInstallDirectory -eq 'yes') {
    $installerArguments += "INSTALLDIR=`"$InstallDirectory`""
}

Write-Log "Running the installer with: $($installerArguments -join ' ')"
$installer = Start-Process -FilePath $InstallerPath -ArgumentList $installerArguments -Wait -PassThru
Write-Log "The installer finished with exit code $($installer.ExitCode)"

Remove-Item -LiteralPath $StagingDirectory -Recurse -Force -ErrorAction SilentlyContinue

if ($LaunchAfterInstall -eq 'yes') {
    Write-Log "Starting Emberr"
    Start-Process -FilePath (Join-Path $InstallDirectory 'Emberr.exe')
}

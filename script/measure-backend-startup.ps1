# 在独立 user.home 和端口上串行测量；FastJit 仅用于对照，不修改桌面默认 JVM 参数。
param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [Parameter(Mandatory=$true)][string]$Label,
    [ValidateRange(1, 20)][int]$Runs = 3,
    [string]$JavaPath = "D:\tool\Java\jdk-17\bin\java.exe",
    [ValidateRange(1024, 65535)][int]$Port = 10829,
    [switch]$FastJit,
    [switch]$Profile,
    [switch]$Record
)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$benchRoot = Join-Path $workspace "logs/startup-benchmark"
if ($Label -notmatch "^[a-zA-Z0-9_-]+$") { throw "Label must contain only letters, numbers, underscore or hyphen" }
if (-not (Test-Path -LiteralPath $Jar)) { throw "Jar not found: $Jar" }
if (-not (Test-Path -LiteralPath $JavaPath)) { throw "Java not found: $JavaPath" }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw "Port $Port is already in use" }
$benchHome = Join-Path $benchRoot 'home'
New-Item -ItemType Directory -Path $benchHome -Force | Out-Null
$env:TEMP = Join-Path $benchRoot "java-tmp"
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$env:TMP = $env:TEMP
$java = $JavaPath
for ($run = 1; $run -le $Runs; $run++) {
    $prefix = Join-Path $benchRoot "$Label-$run"
    $arguments = @('-Xmx1024M', '-noverify', "-Duser.home=$benchHome", '-Dspring.profiles.active=release', '-Dserver.address=127.0.0.1', "-Dserver.port=$Port", '-Dchat2db.mode=DESKTOP')
    if ($FastJit) { $arguments += '-XX:TieredStopAtLevel=1' }
    if ($Profile) { $arguments += '-Dchat2db.startup.profile=true' }
    if ($Record) { $arguments += "-XX:StartFlightRecording=filename=$prefix.jfr,settings=profile,dumponexit=true" }
    $arguments += @('-jar', $Jar)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $process = Start-Process -FilePath $java -ArgumentList ($arguments | ForEach-Object { '"' + $_ + '"' }) -PassThru -WindowStyle Hidden -RedirectStandardOutput "$prefix.out.log" -RedirectStandardError "$prefix.err.log"
    try {
        $ready = $false
        while ($timer.Elapsed.TotalSeconds -lt 60) {
            if ($process.HasExited) { throw "Benchmark exited: $prefix" }
            $content = Get-Content "$prefix.out.log" -Raw -ErrorAction SilentlyContinue
            if ($content -match 'started successfully in (\d+)ms') {
                $readyMs = [int]$Matches[1]
                $ready = $true
                break
            }
            Start-Sleep -Milliseconds 100
        }
        if (-not $ready) { throw "Readiness timeout: $prefix" }
        $requestTimer = [Diagnostics.Stopwatch]::StartNew()
        $system = Invoke-WebRequest "http://127.0.0.1:$Port/api/system" -UseBasicParsing
        $systemMs = $requestTimer.ElapsedMilliseconds
        $requestTimer.Restart()
        $environment = Invoke-WebRequest "http://127.0.0.1:$Port/api/common/environment/list_all" -UseBasicParsing
        $environmentMs = $requestTimer.ElapsedMilliseconds
        $headers = @{ Accept = 'application/json, text/event-stream' }
        $initializeBody = '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"startup-benchmark","version":"1"}}}'
        $requestTimer.Restart()
        $initialize = Invoke-WebRequest "http://127.0.0.1:$Port/api/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body $initializeBody -UseBasicParsing
        $mcpMs = $requestTimer.ElapsedMilliseconds
        $sessionId = @($initialize.Headers['Mcp-Session-Id'])[0]
        if ($sessionId) { $headers['Mcp-Session-Id'] = $sessionId }
        $headers['MCP-Protocol-Version'] = '2025-06-18'
        Invoke-WebRequest "http://127.0.0.1:$Port/api/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body '{"jsonrpc":"2.0","method":"notifications/initialized"}' -UseBasicParsing | Out-Null
        $tools = Invoke-WebRequest "http://127.0.0.1:$Port/api/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' -UseBasicParsing
        $resources = Invoke-WebRequest "http://127.0.0.1:$Port/api/mcp" -Method Post -ContentType 'application/json' -Headers $headers -Body '{"jsonrpc":"2.0","id":3,"method":"resources/list"}' -UseBasicParsing
        $toolJson = $tools.Content.Trim()
        if (-not $toolJson.StartsWith('{')) {
            $toolJson = ($toolJson -split '\r?\n' | Where-Object { $_.StartsWith('data:') } |
                Select-Object -Last 1).Substring(5).Trim()
        }
        $toolPayload = $toolJson | ConvertFrom-Json
        if (@($toolPayload.result.tools).Count -eq 0) { throw "MCP returned no tools" }
        $tools.Content | Set-Content "$prefix.tools.json"
        $resources.Content | Set-Content "$prefix.resources.json"
        [pscustomobject]@{
            Label = $Label
            Run = $run
            ReadyMs = $readyMs
            SystemMs = $systemMs
            EnvironmentMs = $environmentMs
            McpInitializeMs = $mcpMs
            SystemStatus = $system.StatusCode
            EnvironmentStatus = $environment.StatusCode
            McpStatus = $initialize.StatusCode
            ToolCount = @($toolPayload.result.tools).Count
        } | Export-Csv (Join-Path $benchRoot 'measurements.csv') -Append -NoTypeInformation
        Get-Content "$prefix.out.log" | Select-String 'Started Chat2db|Startup|Registered tools'
    } finally {
        if (-not $process.HasExited) {
            try { Invoke-WebRequest "http://127.0.0.1:$Port/api/system/stop?forceQuit=false" -Method Post -UseBasicParsing | Out-Null } catch { }
            if (-not $process.WaitForExit(15000)) { Stop-Process -Id $process.Id -Force }
        }
    }
}

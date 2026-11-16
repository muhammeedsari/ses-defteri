param(
    [Parameter(Mandatory=$true)][string]$AndroidJar,
    [string]$Javac = 'javac',
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$AndroidJar = [IO.Path]::GetFullPath($AndroidJar)
if (-not (Test-Path -LiteralPath $AndroidJar)) { throw 'Provide an Android SDK android.jar.' }
$build = Join-Path $root ('.review-tools/run-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $build -Force | Out-Null
function Sources($name, $files) {
    $list = Join-Path $build ($name + '.txt')
    $quoted = $files | ForEach-Object { '"' + [IO.Path]::GetFullPath($_).Replace('\', '/') + '"' }
    [IO.File]::WriteAllLines($list, [string[]]$quoted, [Text.UTF8Encoding]::new($false))
    return '@' + $list
}
function CheckExit($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)" } }
Push-Location $root
try {
    & node tests/desktop/regression-5211.js; CheckExit 'JavaScript regressions'
    # Only resource IDs are doubled for this compile check. App code compiles against the real SDK.
    $resources = Join-Path $build 'R.java'
    [IO.File]::WriteAllText($resources, 'package app.sesdefteri; public final class R { public static final class mipmap { public static final int ic_rec=1; } public static final class drawable { public static final int ic_stat_rec=2; } }')
    $app = Join-Path $build 'app'
    $list = Sources 'app' (@(Get-ChildItem android/src -Recurse -Filter *.java | ForEach-Object FullName) + $resources)
    & $Javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp $AndroidJar -d $app $list; CheckExit 'Android Java 8 compilation'
    $sep = [IO.Path]::PathSeparator
    $sdk = $app + $sep + $AndroidJar
    $archive = Join-Path $build 'archive'
    $files = @(Get-ChildItem tests/desktop/archive,tests/desktop/jsonshim -Recurse -Filter *.java | ForEach-Object FullName) + 'android/src/app/sesdefteri/Archive.java' + 'tests/desktop/stub/android/net/Uri.java'
    $list = Sources 'archive' $files
    & $Javac -encoding UTF-8 -cp $sdk -d $archive $list; CheckExit 'Archive test compilation'
    & $Java -cp $archive app.sesdefteri.ArchiveTest; CheckExit 'Archive regressions'
    & $Java -cp ($archive + $sep + $sdk) app.sesdefteri.RecoveryTest; CheckExit 'Recorder regressions'
    $memory = Join-Path $build 'memory'
    $files = @(Get-ChildItem tests/desktop/memory,tests/desktop/jsonshim,tests/desktop/stub -Recurse -Filter *.java | Where-Object { $_.FullName.Replace('\','/') -notmatch '/stub/android/content/Context.java$' } | ForEach-Object FullName) + 'tests/desktop/archive/android/content/Context.java' + 'tests/desktop/archive/android/content/ContentResolver.java'
    $list = Sources 'memory' $files
    & $Javac -encoding UTF-8 -cp $sdk -d $memory $list; CheckExit 'Memory test compilation'
    & $Java -cp ($memory + $sep + $sdk) app.sesdefteri.CodecTest; CheckExit 'Encoder EOS regressions'
    & $Java -Xmx40m -cp ($memory + $sep + $sdk) app.sesdefteri.MemoryTest; CheckExit 'Bounded memory regressions'
    Write-Output 'All source regression checks passed. APK/device/native-model tests were not run.'
} finally { Pop-Location }

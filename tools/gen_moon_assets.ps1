# gen_moon_assets.ps1 —— 生成月壤贴图（原版泥土换色）。
#
# 口径：取原版 dirt.png 作底稿，保留噪点与明暗，只把棕色去饱和成月面浅灰；
# 不新增任何外部素材。月壤是泥土换色，所以纹理粒度与泥土一致。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_moon_assets.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot = Split-Path -Parent $PSScriptRoot
$BlockDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\block'
New-Item -ItemType Directory -Force -Path $BlockDir | Out-Null

function ClampByte([double]$v) {
    return [int][Math]::Max(0, [Math]::Min(255, [Math]::Round($v)))
}

function Convert-ToRegolith([System.Drawing.Bitmap]$src) {
    $out = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $l = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            # 压到月面灰的窄亮度带，再带一点冷色偏移
            $v = 92.0 + ($l - 64.0) * 0.62
            $r = ClampByte ($v * 0.97)
            $g = ClampByte ($v * 0.99)
            $b = ClampByte ($v * 1.03)
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
        }
    }
    return $out
}

$jar = Get-ChildItem (Join-Path $ProjRoot 'build\moddev\artifacts') -Filter 'minecraft-patched-*-merged.jar' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $jar) { throw '找不到 build\moddev\artifacts\minecraft-patched-*-merged.jar（先跑一次 gradlew build）' }

$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    $entry = $zip.GetEntry('assets/minecraft/textures/block/dirt.png')
    if ($null -eq $entry) { throw '原版贴图缺项：assets/minecraft/textures/block/dirt.png' }
    $ms = New-Object System.IO.MemoryStream
    $s = $entry.Open(); $s.CopyTo($ms); $s.Dispose(); $ms.Position = 0
    $src = [System.Drawing.Bitmap]::FromStream($ms)
    $bmp = Convert-ToRegolith $src
    $src.Dispose(); $ms.Dispose()
    $target = Join-Path $BlockDir 'moon_regolith.png'
    $bmp.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("  写出 {0}（换色自原版 dirt.png）" -f $target)
} finally {
    $zip.Dispose()
}

Write-Host '完成：月壤贴图 1 张。'

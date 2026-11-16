# gen_stream_icons.ps1 —— 生成粒子流 JEI 图标（原版岩浆换色 + 保留动画）。
#
# 口径：从原版 jar 取 block/lava_still.png 的全部动画帧（16×512），整图按亮度归一化后重着成
#       各流的代表色，输出 textures/block/stream_<id>.png，并复制 lava_still.png.mcmeta，
#       因此图标在 JEI 里会像熔岩一样流动。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_stream_icons.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot = Split-Path -Parent $PSScriptRoot
$BlockDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\block'
New-Item -ItemType Directory -Force -Path $BlockDir | Out-Null
. (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')
$Jar = Get-VanillaJarPath $ProjRoot

function Read-Entry([string]$path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $entry = $zip.Entries | Where-Object { $_.FullName -eq $path } | Select-Object -First 1
        if (-not $entry) { throw "原版 jar 里找不到 $path" }
        $memory = New-Object System.IO.MemoryStream
        $entry.Open().CopyTo($memory)
        $memory.Position = 0
        return $memory
    } finally {
        $zip.Dispose()
    }
}

$textureStream = Read-Entry 'assets/minecraft/textures/block/lava_still.png'
$full = New-Object System.Drawing.Bitmap($textureStream)
$width = $full.Width
$height = $full.Height

$metaStream = Read-Entry 'assets/minecraft/textures/block/lava_still.png.mcmeta'
$reader = New-Object System.IO.StreamReader($metaStream)
$meta = $reader.ReadToEnd()
$reader.Dispose()

function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

$minL = 255.0; $maxL = 0.0
for ($y = 0; $y -lt $height; $y++) {
    for ($x = 0; $x -lt $width; $x++) {
        $c = $full.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        $l = Lum $c
        if ($l -lt $minL) { $minL = $l }
        if ($l -gt $maxL) { $maxL = $l }
    }
}
$span = [Math]::Max(1.0, $maxL - $minL)

# 流类型 → 代表色（顺序与 StreamType 一致）
$colors = [ordered]@{
    muon             = @(0, 220, 220)
    proton           = @(230, 60, 50)
    deuteron         = @(240, 150, 40)
    alpha            = @(240, 220, 70)
    neutron          = @(90, 220, 90)
    lead_ion         = @(150, 90, 220)
    uranium_ion      = @(70, 140, 240)
    polarized_proton = @(240, 120, 200)
    radioactive_ion  = @(170, 255, 80)
}

foreach ($key in $colors.Keys) {
    $r = $colors[$key][0]; $g = $colors[$key][1]; $b = $colors[$key][2]
    $out = New-Object System.Drawing.Bitmap($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $c = $full.GetPixel($x, $y)
            $t = ((Lum $c) - $minL) / $span
            $t = [Math]::Min(1.0, [Math]::Max(0.0, $t))
            $k = 0.30 + 0.95 * $t
            $rr = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($r * $k)))
            $gg = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($g * $k)))
            $bb = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($b * $k)))
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rr, $gg, $bb))
        }
    }
    $path = Join-Path $BlockDir ("stream_{0}.png" -f $key)
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Set-Content -Path ("{0}.mcmeta" -f $path) -Value $meta -Encoding UTF8 -NoNewline
    Write-Host ("  写出 stream_{0}.png（{1}×{2}，动画）" -f $key, $width, $height)
}

$full.Dispose()
$textureStream.Dispose()
$metaStream.Dispose()
Write-Host '粒子流动画图标已生成。'

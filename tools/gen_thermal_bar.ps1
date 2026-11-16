# gen_thermal_bar.ps1 —— 生成 Jade 联动的「热压差」进度条填充贴图（橙色）。
#
# 做法：取 Jade 自带 FE 能量条的填充贴图 jade:gui/sprites/energy_progress.png
# （16×16、全不透明的暗红渐变），只把色相换成橙色、形状与明暗层次原样保留 ——
# 于是 HUD 里的进度条与 Jade 能量槽同款，只是颜色为橙。
#
# 输出：src/main/resources/assets/stellar_evolution/textures/gui/sprites/thermal_progress.png
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_thermal_bar.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

$ProjRoot = Split-Path -Parent $PSScriptRoot
$jade = Get-ChildItem (Join-Path $ProjRoot 'run\mods') -Filter 'jade-*.jar' -ErrorAction SilentlyContinue |
        Select-Object -First 1
if (-not $jade) { throw '找不到 Jade jar：run\mods\jade-*.jar（编译期依赖，缺了取不到源贴图）' }

$entryName = 'assets/jade/textures/gui/sprites/energy_progress.png'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jade.FullName)
try {
    $entry = $zip.Entries | Where-Object { $_.FullName -eq $entryName } | Select-Object -First 1
    if (-not $entry) { throw "Jade jar 里没有 $entryName" }
    $ms = New-Object System.IO.MemoryStream
    $entry.Open().CopyTo($ms)
    $src = New-Object System.Drawing.Bitmap([System.Drawing.Image]::FromStream($ms))
} finally {
    $zip.Dispose()
}

$outDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\gui\sprites'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# 源是暗红渐变（G=B=0，R 落在 102..166）：按下标 t(0..1) 映射到三种色带 ——
#   ① 橙色 thermal_progress（DP 槽）：R 200→255、G 90→150、B 10→30
#   ② 紫色 fuel_progress（燃料 / 肥料槽）：R 150→210、G 80→120、B 210→255
#   ③ 绿色 recipe_progress（大机器「剩余配方时长」槽）：R 80→150、G 190→255、B 80→120
function New-Bar([int]$r0, [int]$r1, [int]$g0, [int]$g1, [int]$b0, [int]$b1, [string]$file) {
    $dst = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $t = [Math]::Min(1.0, [Math]::Max(0.0, ($c.R - 102) / 64.0))
            $r = [int][Math]::Round($r0 + ($r1 - $r0) * $t)
            $g = [int][Math]::Round($g0 + ($g1 - $g0) * $t)
            $b = [int][Math]::Round($b0 + ($b1 - $b0) * $t)
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
        }
    }
    $outFile = Join-Path $outDir $file
    $dst.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
    $dst.Dispose()
    Write-Host "已生成 $outFile"
}

New-Bar 200 255 90 150 10 30 'thermal_progress.png'
New-Bar 150 210 80 120 210 255 'fuel_progress.png'
New-Bar 80 150 190 255 80 120 'recipe_progress.png'

$src.Dispose()

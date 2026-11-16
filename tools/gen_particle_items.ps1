# gen_particle_items.ps1 —— 生成粒子线物品图标（压力罐）。
#
# 口径：取 tools/templates/fluid_cell.png 作形状模板，按模板自身亮度归一化后重着成钢灰色，
#       保留高光与阴影；物品模型走 1.21.4+ 的 items/<name>.json 物品模型定义。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_particle_items.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$ItemDir   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\item'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$Template  = Join-Path $ProjRoot 'tools\templates\fluid_cell.png'

New-Item -ItemType Directory -Force -Path $ItemDir, $ModelsDir, $ItemsDir | Out-Null

function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

function New-Tinted([string]$src, [int]$r, [int]$g, [int]$b, [string]$outPath) {
    if (-not (Test-Path $src)) { throw "缺少模板：$src" }
    $bmp = [System.Drawing.Bitmap]::FromFile($src)
    $minL = 255.0; $maxL = 0.0
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $l = Lum $c
            if ($l -lt $minL) { $minL = $l }
            if ($l -gt $maxL) { $maxL = $l }
        }
    }
    $span = [Math]::Max(1.0, $maxL - $minL)
    $out = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $t = ((Lum $c) - $minL) / $span
            $t = [Math]::Min(1.0, [Math]::Max(0.0, $t))
            $k = 0.55 + 0.90 * $t
            $rr = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($r * $k)))
            $gg = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($g * $k)))
            $bb = [Math]::Max(0, [Math]::Min(255, [int][Math]::Round($b * $k)))
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rr, $gg, $bb))
        }
    }
    $bmp.Dispose()
    $out.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    Write-Host ("  写出 {0}" -f (Split-Path -Leaf $outPath))
}

function Write-FlatItemModel([string]$name) {
    $model = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "stellar_evolution:item/$name"
    }
}
"@
    Set-Content -Path (Join-Path $ModelsDir ("{0}.json" -f $name)) -Value $model -Encoding UTF8

    $def = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:item/$name"
    }
}
"@
    Set-Content -Path (Join-Path $ItemsDir ("{0}.json" -f $name)) -Value $def -Encoding UTF8
}

New-Tinted $Template 150 168 188 (Join-Path $ItemDir 'pressure_tank.png')
Write-FlatItemModel 'pressure_tank'

# 束流增压模块：同一形状重着成琥珀色
New-Tinted $Template 196 148 72 (Join-Path $ItemDir 'booster_module.png')
Write-FlatItemModel 'booster_module'
Write-Host '粒子线物品图标已生成。'

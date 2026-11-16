# gen_basic_materials.ps1 —— 原版金属型材补充（2026-10-24）。
#
# 输出：铁板 / 金板 / 金线 / 下界合金板 / 下界合金杆。
# 口径与 copper / steel 一致：`模板/材料/浅色/material_<shape>.png` 灰度模板按基色上色，
# 保留模板自带高光与阴影；模型 = item/generated，物品定义走 items/<id>.json。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_basic_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot   = Split-Path -Parent $PSScriptRoot
$AssetRoot  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution'
$ItemDir    = Join-Path $AssetRoot 'textures\item'
$ModelsDir  = Join-Path $AssetRoot 'models\item'
$ItemsDir   = Join-Path $AssetRoot 'items'
$TplLight   = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $ModelsDir, $ItemsDir | Out-Null

function New-Bmp([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Col([int]$r, [int]$g, [int]$b, [int]$a) {
    return [System.Drawing.Color]::FromArgb($a,
        [Math]::Max(0, [Math]::Min(255, $r)), [Math]::Max(0, [Math]::Min(255, $g)), [Math]::Max(0, [Math]::Min(255, $b)))
}
function Px($bmp, [int]$x, [int]$y, $c) {
    if ($x -ge 0 -and $y -ge 0 -and $x -lt $bmp.Width -and $y -lt $bmp.Height) { $bmp.SetPixel($x, $y, $c) }
}
function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

function New-TintedItem([string]$shape, [int]$r, [int]$g, [int]$b) {
    $src = Join-Path $TplLight ("material_{0}.png" -f $shape)
    if (-not (Test-Path $src)) { throw "missing material template: $src" }
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
    $out = New-Bmp $bmp.Width $bmp.Height
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $t = [Math]::Min(1.0, [Math]::Max(0.0, (((Lum $c) - $minL) / $span)))
            $k = 0.55 + 0.90 * $t
            Px $out $x $y (Col ([int][Math]::Round($r * $k)) ([int][Math]::Round($g * $k)) ([int][Math]::Round($b * $k)) $c.A)
        }
    }
    $bmp.Dispose()
    return $out
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

$items = @(
    @{ id = 'iron_plate';      shape = 'plate'; rgb = @(188, 192, 198) },
    @{ id = 'gold_plate';      shape = 'plate'; rgb = @(238, 196,  72) },
    @{ id = 'gold_wire';       shape = 'wire';  rgb = @(244, 202,  80) },
    @{ id = 'netherite_plate'; shape = 'plate'; rgb = @( 92,  86, 100) },
    @{ id = 'netherite_rod';   shape = 'rod';   rgb = @( 88,  82,  96) }
)

foreach ($it in $items) {
    $bmp = New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]
    $bmp.Save((Join-Path $ItemDir ("{0}.png" -f $it.id)), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.png / .json (template={1})" -f $it.id, $it.shape)
}
Write-Host 'Done: 5 basic metal parts.'

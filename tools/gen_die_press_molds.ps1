# gen_die_press_molds.ps1 —— 压模机模具（板 / 杆 / 齿轮）的 16×16 物品图标。
#
# 口径（照 gen_intermediate_items.ps1 的钢系平面件）：
#   模板取外部 extruder mold 素材，按模板自身亮度范围归一化后按**深钢色**上色；
#   比 steel_ingot / steel_plate 的钢基色（170,174,181）整体压暗，模具作为工装与材料区分。
#
# 输出：
#   src/main/resources/assets/stellar_evolution/textures/item/<id>.png
#   src/main/resources/assets/stellar_evolution/models/item/<id>.json
#   src/main/resources/assets/stellar_evolution/items/<id>.json
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_die_press_molds.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $ModelsDir, $ItemsDir | Out-Null

function Col([int]$r, [int]$g, [int]$b, [int]$a) {
    return [System.Drawing.Color]::FromArgb($a,
        [Math]::Max(0, [Math]::Min(255, $r)), [Math]::Max(0, [Math]::Min(255, $g)), [Math]::Max(0, [Math]::Min(255, $b)))
}
function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

function New-TintedMold([string]$shape, [int]$r, [int]$g, [int]$b) {
    $src = Join-Path $TplLight ("extruder_mold_{0}.png" -f $shape)
    if (-not (Test-Path $src)) { throw "缺少模具模板：$src" }
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
            $out.SetPixel($x, $y, (Col ([int][Math]::Round($r * $k)) ([int][Math]::Round($g * $k)) ([int][Math]::Round($b * $k)) $c.A))
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

# 深钢色：比 steel_* 的 170,174,181 压暗约 30%，保留模板的高光 / 阴影层次
$DARK_STEEL = @(118, 122, 129)

$molds = @(
    @{ id = 'plate_mold'; shape = 'plate' },  # 板模具
    @{ id = 'rod_mold';   shape = 'rod'   },  # 杆模具
    @{ id = 'gear_mold';  shape = 'gear'  }   # 齿轮模具
)

foreach ($m in $molds) {
    $bmp = New-TintedMold $m.shape $DARK_STEEL[0] $DARK_STEEL[1] $DARK_STEEL[2]
    $bmp.Save((Join-Path $ItemDir ("{0}.png" -f $m.id)), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-FlatItemModel $m.id
    Write-Host ("  {0}.png / .json（模板={1}）" -f $m.id, $m.shape)
}

Write-Host ("完成：{0} 个模具图标。" -f $molds.Count)

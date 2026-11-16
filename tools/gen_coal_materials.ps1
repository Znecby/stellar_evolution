# gen_coal_materials.ps1 —— 煤化工线（C 线）物品图标 / 流体贴图 / 桶图标。
#
# 口径照 gen_petro_materials.ps1：
#   · 物品：灰度材料模板上色（焦炭 = dust，尿素 = nugget）；
#   · 流体：原版水动画条换色（tools/lib_water_fluid.ps1）→ block/<name>_still.png + _flow.png；
#   · 桶：以原版水桶贴图为模板重着色；
#   · 物品模型：models/item/<id>.json（generated）+ items/<id>.json（两份）。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_coal_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$BlockDir  = Join-Path $TexRoot 'block'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $BlockDir, $ModelsDir, $ItemsDir | Out-Null
. (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')

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
function Save($bmp, [string]$path) {
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("  写出 {0}" -f (Split-Path -Leaf $path))
}
function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

# 灰度模板 + 目标基色（保留模板的倒角 / 高光 / 阴影）
function New-TintedItem([string]$shape, [int]$r, [int]$g, [int]$b) {
    $src = Join-Path $TplLight ("material_{0}.png" -f $shape)
    if (-not (Test-Path $src)) { throw "缺少材料模板：$src" }
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
            $t = ((Lum $c) - $minL) / $span
            $t = [Math]::Min(1.0, [Math]::Max(0.0, $t))
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

# ── 1) 平面物品：焦炭 / 尿素 ─────────────────────────────────────────────────
$flatItems = @(
    @{ id = 'coke'; shape = 'dust';   rgb = @( 42,  40,  42) },  # 焦炭：近黑焦粒
    @{ id = 'urea'; shape = 'nugget'; rgb = @(240, 240, 232) },   # 尿素：白色颗粒
    # ---- 2026-11-11 批次 1
    @{ id = 'calcium_carbide';   shape = 'nugget'; rgb = @(108, 112, 118) }, # 电石：灰块
    @{ id = 'calcium_hydroxide'; shape = 'dust';   rgb = @(236, 236, 230) }, # 氢氧化钙：白粉
    @{ id = 'activated_carbon';  shape = 'dust';   rgb = @( 28,  28,  30) }, # 活性炭：黑粉
    @{ id = 'needle_coke';       shape = 'dust';   rgb = @( 46,  44,  48) }, # 针状焦：近黑带光
    @{ id = 'graphite_electrode'; shape = 'rod';   rgb = @( 48,  48,  52) }, # 石墨电极：石墨棒
    @{ id = 'pvc';               shape = 'ingot';  rgb = @(236, 236, 230) }, # 聚氯乙烯：白色塑料
    @{ id = 'coal_dust';         shape = 'dust';   rgb = @( 30,  28,  30) }, # 煤炭粉：近黑
    @{ id = 'charcoal_dust';     shape = 'dust';   rgb = @( 42,  39,  38) }  # 木炭粉：深灰
)

foreach ($it in $flatItems) {
    $bmp = New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]
    Save $bmp (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json（models/item + items，模板={1}）" -f $it.id, $it.shape)
}

# ── 2) 桶图标（重着色模板） ─────────────────────────────────────────────────
$BucketTemplate = Join-Path $PSScriptRoot 'templates\water_bucket.png'

function New-BucketIcon([int]$r, [int]$g, [int]$bl) {
    $src = [System.Drawing.Bitmap]::FromFile($BucketTemplate)
    $w = $src.Width; $h = $src.Height
    $minLum = [int]::MaxValue; $maxLum = -1
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
                $lum = $c.R + $c.G + $c.B
                if ($lum -lt $minLum) { $minLum = $lum }
                if ($lum -gt $maxLum) { $maxLum = $lum }
            }
        }
    }
    $span = [Math]::Max(1, ($maxLum - $minLum))
    $bmp = New-Bmp $w $h
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
                $f = 0.6 + 0.4 * ((($c.R + $c.G + $c.B) - $minLum) / $span)
                Px $bmp $x $y (Col ([int]($r * $f)) ([int]($g * $f)) ([int]($bl * $f)) 255)
            } else {
                Px $bmp $x $y $c
            }
        }
    }
    $src.Dispose()
    return $bmp
}

# ── 3) 流体贴图 + 桶 ────────────────────────────────────────────────────────
$fluids = @(
    @{ name = 'syngas';         rgb = @( 55,  90, 130) }, # 合成气：深钢蓝
    @{ name = 'clean_syngas';   rgb = @( 80, 196, 206) }, # 净化合成气：亮青
    @{ name = 'coal_tar';       rgb = @( 90,  25,  35) }, # 煤焦油：深酒红
    @{ name = 'methanol';       rgb = @(145, 120, 210) }, # 甲醇：深紫
    @{ name = 'carbon_dioxide'; rgb = @(145, 155, 135) }, # 二氧化碳：灰绿
    @{ name = 'naphthalene';    rgb = @(220, 196, 160) }, # 萘：浅奶油桃
    # ---- 2026-11-11 批次 1
    @{ name = 'coke_oven_gas';  rgb = @( 86,  96, 108) }, # 焦炉煤气：灰蓝
    @{ name = 'producer_gas';   rgb = @( 96, 104,  92) }, # 发生炉煤气：灰绿
    @{ name = 'dme';            rgb = @(120, 160, 190) }, # 二甲醚：淡蓝
    @{ name = 'sng';            rgb = @(150, 190, 200) }, # 合成天然气：浅青
    @{ name = 'synthetic_jet';  rgb = @(170, 150,  80) }, # 合成航煤：金
    @{ name = 'acetylene';      rgb = @(140, 120, 180) }, # 乙炔：紫
    @{ name = 'vcm';            rgb = @(180, 200, 190) }, # 氯乙烯：浅青灰
    @{ name = 'vinyl_acetate';  rgb = @(170, 150, 190) }, # 醋酸乙烯：淡紫
    @{ name = 'btx';            rgb = @(130, 120, 160) }, # 混合芳烃：蓝紫
    @{ name = 'coal_slurry';    rgb = @( 62,  60,  56) }, # 煤水浆：深灰泥
    @{ name = 'coal_liquefied_oil'; rgb = @( 62,  44,  26) } # 煤液化油：深琥珀
)

foreach ($f in $fluids) {
    $r = [int]$f.rgb[0]; $g = [int]$f.rgb[1]; $bl = [int]$f.rgb[2]

    Write-WaterFluidTextures -BlockDir $BlockDir -Name $f.name -Rgb @($r, $g, $bl) -ProjRoot $ProjRoot

    $bucket = "{0}_bucket" -f $f.name
    Save (New-BucketIcon $r $g $bl) (Join-Path $ItemDir ("{0}.png" -f $bucket))

    $bucketModel = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "stellar_evolution:item/$bucket"
    }
}
"@
    Set-Content -Path (Join-Path $ModelsDir ("{0}.json" -f $bucket)) -Value $bucketModel -Encoding UTF8

    $bucketDef = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:item/$bucket"
    }
}
"@
    Set-Content -Path (Join-Path $ItemsDir ("{0}.json" -f $bucket)) -Value $bucketDef -Encoding UTF8
}

Write-Host ("完成：平面物品 {0} 张、流体贴图 {1} 张、桶图标 {1} 张。" -f `
    $flatItems.Count, $fluids.Count)

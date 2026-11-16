﻿# gen_welding_assets.ps1 —— 焊接线资源生成（2026-11-06）。
#   · 从外部科技模组的「双层铜板 / 致密铜板」提取贴图 → 灰度化成材料模板（深色 / 浅色）；
#   · 焊接板 = 浅色模板（灰度）；坚固钨焊接板 = 浅色模板按钨基色重新上色；
#   · 氧气流体贴图（原版水动画条换色）+ 氧气桶图标；
#   · 焊接机 1×3 界面底图（176×166，含 3 个料仓格、进度箭头、氧气槽、玩家背包格）。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_welding_assets.ps1

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$SourceJar  = 'C:\MCmod项目\my_mod\run\mods\ftb-industrial-contraptions-26.1.2.10.jar'
$TplDark   = Join-Path $ProjRoot '模板\材料\深色'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
$AssetRoot = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution'
$ItemDir   = Join-Path $AssetRoot 'textures\item'
$BlockDir  = Join-Path $AssetRoot 'textures\block'
$GuiDir    = Join-Path $AssetRoot 'textures\gui'
$ModelsDir = Join-Path $AssetRoot 'models\item'
$ItemsDir  = Join-Path $AssetRoot 'items'

New-Item -ItemType Directory -Force -Path $TplDark, $TplLight, $ItemDir, $BlockDir, $GuiDir, $ModelsDir, $ItemsDir | Out-Null

function New-Bmp([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}

function Lum([System.Drawing.Color]$c) {
    return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
}

function Read-JarEntryBytes([string]$jar, [string]$entry) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $e = $zip.GetEntry($entry)
        if ($null -eq $e) { throw "jar 内缺少：$entry" }
        $ms = New-Object System.IO.MemoryStream
        $e.Open().CopyTo($ms)
        return ,$ms.ToArray()
    } finally { $zip.Dispose() }
}

# ============================================================ 1) 外部双层铜板 → 灰度模板

$bytes = Read-JarEntryBytes $SourceJar 'assets/ftbic/textures/item/dense_copper_plate.png'
$src = [System.Drawing.Bitmap]::FromStream((New-Object System.IO.MemoryStream(,$bytes)))
$light = New-Bmp $src.Width $src.Height
$dark  = New-Bmp $src.Width $src.Height
for ($y = 0; $y -lt $src.Height; $y++) {
    for ($x = 0; $x -lt $src.Width; $x++) {
        $c = $src.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        $l = [int][Math]::Round(80 + (Lum $c) * 153.0 / 255.0)
        $l = [Math]::Max(0, [Math]::Min(255, $l))
        $light.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $l, $l, $l))
        $d = [Math]::Max(0, $l - 65)
        $dark.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $d, $d, $d))
    }
}
$src.Dispose()
$light.Save((Join-Path $TplLight 'double_copper_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$dark.Save((Join-Path $TplDark  'double_copper_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host '  材料模板：模板/材料/浅色 + 深色/double_copper_plate.png'

# ============================================================ 2) 物品贴图

function New-TintedFromLight([int]$r, [int]$g, [int]$b) {
    $minL = 255.0; $maxL = 0.0
    for ($y = 0; $y -lt $light.Height; $y++) {
        for ($x = 0; $x -lt $light.Width; $x++) {
            $c = $light.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $l = Lum $c
            if ($l -lt $minL) { $minL = $l }
            if ($l -gt $maxL) { $maxL = $l }
        }
    }
    $span = [Math]::Max(1.0, $maxL - $minL)
    $out = New-Bmp $light.Width $light.Height
    for ($y = 0; $y -lt $light.Height; $y++) {
        for ($x = 0; $x -lt $light.Width; $x++) {
            $c = $light.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $t = [Math]::Min(1.0, [Math]::Max(0.0, ((Lum $c) - $minL) / $span))
            $k = 0.55 + 0.90 * $t
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A,
                    [int][Math]::Round($r * $k), [int][Math]::Round($g * $k), [int][Math]::Round($b * $k)))
        }
    }
    return $out
}

$tungsten = New-TintedFromLight 120 126 136
$tungsten.Save((Join-Path $ItemDir 'rugged_tungsten_welding_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$tungsten.Dispose()

# 坚固铅 / 高速钢 / 不锈钢焊接板（2026-11-08）：同一灰度模板，按各自基色上色
$lead = New-TintedFromLight 105 110 125
$lead.Save((Join-Path $ItemDir 'rugged_lead_welding_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$lead.Dispose()
$hss = New-TintedFromLight 130 140 155
$hss.Save((Join-Path $ItemDir 'rugged_high_speed_steel_welding_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$hss.Dispose()
$stainless = New-TintedFromLight 160 165 175
$stainless.Save((Join-Path $ItemDir 'rugged_stainless_steel_welding_plate.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$stainless.Dispose()

$light.Dispose()
$dark.Dispose()
Write-Host '  物品贴图：rugged_tungsten / lead / high_speed_steel / stainless_steel welding_plate'

# ============================================================ 3) 氧气流体 + 桶

. (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')
Write-WaterFluidTextures -BlockDir $BlockDir -Name 'oxygen' -Rgb @(180, 220, 255) -ProjRoot $ProjRoot

$BucketTemplate = Join-Path $PSScriptRoot 'templates\water_bucket.png'
$bucketSrc = [System.Drawing.Bitmap]::FromFile($BucketTemplate)
$bucket = New-Bmp $bucketSrc.Width $bucketSrc.Height
$minLum = [int]::MaxValue; $maxLum = -1
for ($y = 0; $y -lt $bucketSrc.Height; $y++) {
    for ($x = 0; $x -lt $bucketSrc.Width; $x++) {
        $c = $bucketSrc.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
            $lum = $c.R + $c.G + $c.B
            if ($lum -lt $minLum) { $minLum = $lum }
            if ($lum -gt $maxLum) { $maxLum = $lum }
        }
    }
}
$span = [Math]::Max(1, ($maxLum - $minLum))
for ($y = 0; $y -lt $bucketSrc.Height; $y++) {
    for ($x = 0; $x -lt $bucketSrc.Width; $x++) {
        $c = $bucketSrc.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
            $f = 0.6 + 0.4 * ((($c.R + $c.G + $c.B) - $minLum) / $span)
            $bucket.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
                    [int](180 * $f), [int](220 * $f), [int](255 * $f)))
        } else {
            $bucket.SetPixel($x, $y, $c)
        }
    }
}
$bucketSrc.Dispose()
$bucket.Save((Join-Path $ItemDir 'oxygen_bucket.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$bucket.Dispose()
Write-Host '  流体：oxygen_still/_flow (+mcmeta) + oxygen_bucket'

# 助焊剂（2026-11-08）：硼砂 + 水，调浆机制成，焊接消耗 125 mB
Write-WaterFluidTextures -BlockDir $BlockDir -Name 'flux' -Rgb @(205, 150, 55) -ProjRoot $ProjRoot
$fluxSrc = [System.Drawing.Bitmap]::FromFile($BucketTemplate)
$fluxMinLum = [int]::MaxValue; $fluxMaxLum = -1
for ($y = 0; $y -lt $fluxSrc.Height; $y++) {
    for ($x = 0; $x -lt $fluxSrc.Width; $x++) {
        $c = $fluxSrc.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
            $lum = $c.R + $c.G + $c.B
            if ($lum -lt $fluxMinLum) { $fluxMinLum = $lum }
            if ($lum -gt $fluxMaxLum) { $fluxMaxLum = $lum }
        }
    }
}
$flux = New-Bmp $fluxSrc.Width $fluxSrc.Height
$fluxSpan = [Math]::Max(1, ($fluxMaxLum - $fluxMinLum))
for ($y = 0; $y -lt $fluxSrc.Height; $y++) {
    for ($x = 0; $x -lt $fluxSrc.Width; $x++) {
        $c = $fluxSrc.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        if ($c.B -gt ($c.R + 40) -and $c.B -gt ($c.G + 40)) {
            $f = 0.6 + 0.4 * ((($c.R + $c.G + $c.B) - $fluxMinLum) / $fluxSpan)
            $flux.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255,
                    [int](205 * $f), [int](150 * $f), [int](55 * $f)))
        } else {
            $flux.SetPixel($x, $y, $c)
        }
    }
}
$fluxSrc.Dispose()
$flux.Save((Join-Path $ItemDir 'flux_bucket.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$flux.Dispose()
Write-Host '  流体：flux_still/_flow (+mcmeta) + flux_bucket'

# ============================================================ 4) 物品模型 / 物品模型定义

function Write-ItemModels([string]$id) {
    $model = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "stellar_evolution:item/$id"
    }
}
"@
    Set-Content -Path (Join-Path $ModelsDir ("{0}.json" -f $id)) -Value $model -Encoding UTF8
    $def = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:item/$id"
    }
}
"@
    Set-Content -Path (Join-Path $ItemsDir ("{0}.json" -f $id)) -Value $def -Encoding UTF8
}

Write-ItemModels 'rugged_tungsten_welding_plate'
Write-ItemModels 'rugged_lead_welding_plate'
Write-ItemModels 'rugged_high_speed_steel_welding_plate'
Write-ItemModels 'rugged_stainless_steel_welding_plate'
Write-ItemModels 'oxygen_bucket'
Write-ItemModels 'flux_bucket'

# ============================================================ 5) 焊接机界面底图（原版发射器底图合成）

$jar = Get-VanillaJarPath $ProjRoot
$dispenserBytes = Read-JarEntryBytes $jar 'assets/minecraft/textures/gui/container/dispenser.png'
$gui = [System.Drawing.Bitmap]::FromStream((New-Object System.IO.MemoryStream(,$dispenserBytes)))
$gfx = [System.Drawing.Graphics]::FromImage($gui)

# 1) 抹掉 3×3 网格的第 2、3 行（只保留第一行 = 1×3）。
#    槽位贴图比槽格大 1px（左/上各 1px 边框），所以从 x=61 / y=35 开始整块覆盖，
#    否则会留下一条 1px 的深色竖线（用户看到的「黑线」）。
$panel = $gui.GetPixel(150, 17)
$panelBrush = New-Object System.Drawing.SolidBrush($panel)
$gfx.FillRectangle($panelBrush, 61, 35, 56, 37)
$panelBrush.Dispose()

# 2) 两条原版风格内嵌条（进度 / 氧气）
function Draw-Bar([int]$x, [int]$y, [int]$w, [int]$h) {
    $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 55, 55, 55))
    $gfx.FillRectangle($b, $x, $y, $w, $h)
    $b.Dispose()
    $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 139, 139, 139))
    $gfx.FillRectangle($b, ($x + 1), ($y + 1), ($w - 2), ($h - 2))
    $b.Dispose()
    $b = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 255, 255, 255))
    $gfx.FillRectangle($b, ($x + 1), ($y + 1), ($w - 2), 1)
    $gfx.FillRectangle($b, ($x + 1), ($y + 1), 1, ($h - 2))
    $b.Dispose()
}
Draw-Bar 62 40 54 8
Draw-Bar 62 52 54 8

$gfx.Dispose()
$gui.Save((Join-Path $GuiDir 'welding_machine.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$gui.Dispose()
Write-Host '  界面底图：textures/gui/welding_machine.png（原版发射器界面合成）'

Write-Host '完成：焊接线资源已生成。'

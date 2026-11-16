# gen_tungsten_materials.ps1 —— 钨全套贴图 / 模型（2026-10-24）。
#
# 口径（与项目其它材料一致）：
#   · 物品：`模板/材料/浅色/material_<shape>.png` 灰度模板按钨的冷灰蓝基色上色；
#   · 粗钨：原版 raw_iron 换色；钨精砂：原版 prismarine_crystals 换色；
#   · 矿石：原版 iron_ore / deepslate_iron_ore「石底 + 成簇矿斑」，只把矿斑换成钨色；
#   · 钨块：金属块模板上色；
#   · 富钨精矿浆：原版水动画条换色（tools/lib_water_fluid.ps1），桶图标用水桶模板换色。
#
# 输出：
#   textures/item/raw_tungsten.png, tungsten_{ingot,nugget,dust,plate,rod,wire,gear}.png,
#   tungsten_concentrate_sand.png, rich_tungsten_concentrate_slurry_bucket.png
#   textures/block/tungsten_ore.png, deepslate_tungsten_ore.png, tungsten_block.png,
#   rich_tungsten_concentrate_slurry_still.png / _flow.png (+ .mcmeta)
#   以及 models/item、items、blockstates、models/block 的 JSON。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_tungsten_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot   = Split-Path -Parent $PSScriptRoot
$AssetRoot  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution'
$TexRoot    = Join-Path $AssetRoot 'textures'
$ItemDir    = Join-Path $TexRoot 'item'
$BlockDir   = Join-Path $TexRoot 'block'
$ModelsDir  = Join-Path $AssetRoot 'models\item'
$BlockModels= Join-Path $AssetRoot 'models\block'
$Blockstates= Join-Path $AssetRoot 'blockstates'
$ItemsDir   = Join-Path $AssetRoot 'items'
$TplLight   = Join-Path $ProjRoot '模板\材料\浅色'
$BucketTemplate = Join-Path $PSScriptRoot 'templates\water_bucket.png'
New-Item -ItemType Directory -Force -Path $ItemDir, $BlockDir, $ModelsDir, $BlockModels, $Blockstates, $ItemsDir | Out-Null
. (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')

$Jar = Get-VanillaJarPath $ProjRoot

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
    Write-Host ("  wrote {0}" -f (Split-Path -Leaf $path))
}
function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

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

function Write-BlockModel([string]$name) {
    $model = @"
{
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "stellar_evolution:block/$name"
    }
}
"@
    Set-Content -Path (Join-Path $BlockModels ("{0}.json" -f $name)) -Value $model -Encoding UTF8
    $state = @"
{
    "variants": {
        "": { "model": "stellar_evolution:block/$name" }
    }
}
"@
    Set-Content -Path (Join-Path $Blockstates ("{0}.json" -f $name)) -Value $state -Encoding UTF8
    $def = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/$name"
    }
}
"@
    Set-Content -Path (Join-Path $ItemsDir ("{0}.json" -f $name)) -Value $def -Encoding UTF8
}

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

function New-RecoloredVanilla([string]$entry, [int]$r, [int]$g, [int]$b) {
    $bytes = Read-JarEntryBytes $Jar $entry
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    $src = [System.Drawing.Bitmap]::FromStream($ms)
    $minL = 255.0; $maxL = 0.0
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $l = Lum $c
            if ($l -lt $minL) { $minL = $l }
            if ($l -gt $maxL) { $maxL = $l }
        }
    }
    $span = [Math]::Max(1.0, $maxL - $minL)
    $out = New-Bmp $src.Width $src.Height
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $t = [Math]::Min(1.0, [Math]::Max(0.0, (((Lum $c) - $minL) / $span)))
            $k = 0.55 + 0.90 * $t
            Px $out $x $y (Col ([int][Math]::Round($r * $k)) ([int][Math]::Round($g * $k)) ([int][Math]::Round($b * $k)) $c.A)
        }
    }
    $src.Dispose(); $ms.Dispose()
    return $out
}

function Lerp3($a, $b, [double]$t) {
    return @(
        [int]([Math]::Round($a[0] + ($b[0] - $a[0]) * $t)),
        [int]([Math]::Round($a[1] + ($b[1] - $a[1]) * $t)),
        [int]([Math]::Round($a[2] + ($b[2] - $a[2]) * $t))
    )
}
function Is-OrePixel($c) {
    if ($c.A -eq 0) { return $false }
    return (($c.R - $c.B) -ge 16) -and (($c.R - $c.G) -ge 6)
}
function Convert-Ore($srcBmp, $light, $body, $dark) {
    $w = $srcBmp.Width; $h = $srcBmp.Height
    $min = 255.0; $max = 0.0
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $srcBmp.GetPixel($x, $y)
            if (-not (Is-OrePixel $c)) { continue }
            $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
            if ($lum -lt $min) { $min = $lum }
            if ($lum -gt $max) { $max = $lum }
        }
    }
    $span = [Math]::Max(1.0, ($max - $min))
    $bmp = New-Bmp $w $h
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $srcBmp.GetPixel($x, $y)
            if (Is-OrePixel $c) {
                $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
                $t = (($lum - $min) / $span)
                if ($t -lt 0.5) { $rgb = Lerp3 $dark $body ($t * 2.0) }
                else { $rgb = Lerp3 $body $light (($t - 0.5) * 2.0) }
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rgb[0], $rgb[1], $rgb[2]))
            } else {
                $bmp.SetPixel($x, $y, $c)
            }
        }
    }
    return $bmp
}

# 钨的冷灰蓝三档（矿石矿斑）
$OreLight = @(176, 184, 196)
$OreBody  = @(122, 130, 142)
$OreDark  = @( 72,  78,  88)

# ---- 1) 物品
$items = @(
    @{ id = 'tungsten_ingot'; shape = 'ingot';  rgb = @(118, 124, 134) },
    @{ id = 'tungsten_nugget';shape = 'nugget'; rgb = @(126, 132, 142) },
    @{ id = 'tungsten_dust';  shape = 'dust';   rgb = @( 96, 102, 112) },
    @{ id = 'tungsten_plate'; shape = 'plate';  rgb = @(120, 126, 136) },
    @{ id = 'tungsten_rod';   shape = 'rod';    rgb = @(112, 118, 128) },
    @{ id = 'tungsten_wire';  shape = 'wire';   rgb = @(104, 110, 120) },
    @{ id = 'tungsten_gear';  shape = 'gear';   rgb = @(116, 122, 132) }
)
foreach ($it in $items) {
    Save (New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]) (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (template={1})" -f $it.id, $it.shape)
}

# 粗钨（原版粗铁换色）
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 110 116 126) `
    (Join-Path $ItemDir 'raw_tungsten.png')
Write-FlatItemModel 'raw_tungsten'

# 钨精砂（原版海晶砂换色）
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/prismarine_crystals.png' 132 140 152) `
    (Join-Path $ItemDir 'tungsten_concentrate_sand.png')
Write-FlatItemModel 'tungsten_concentrate_sand'

# ---- 2) 方块：矿石 / 深板岩矿石 / 钨块
$zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
try {
    foreach ($pair in @(
            @{ entry = 'assets/minecraft/textures/block/iron_ore.png';           out = 'tungsten_ore.png' },
            @{ entry = 'assets/minecraft/textures/block/deepslate_iron_ore.png'; out = 'deepslate_tungsten_ore.png' })) {
        $e = $zip.GetEntry($pair.entry)
        if ($null -eq $e) { throw ("原版贴图缺项：{0}" -f $pair.entry) }
        $ms = New-Object System.IO.MemoryStream
        $s = $e.Open(); $s.CopyTo($ms); $s.Dispose(); $ms.Position = 0
        $src = [System.Drawing.Bitmap]::FromStream($ms)
        $bmp = Convert-Ore $src $OreLight $OreBody $OreDark
        $src.Dispose(); $ms.Dispose()
        Save $bmp (Join-Path $BlockDir $pair.out)
    }
} finally { $zip.Dispose() }

Save (New-TintedItem 'block' 112 118 128) (Join-Path $BlockDir 'tungsten_block.png')

Write-BlockModel 'tungsten_ore'
Write-BlockModel 'deepslate_tungsten_ore'
Write-BlockModel 'tungsten_block'

# ---- 3) 流体 + 桶
$fluidRgb = @(140, 146, 158)
Write-WaterFluidTextures -BlockDir $BlockDir -Name 'rich_tungsten_concentrate_slurry' -Rgb $fluidRgb -ProjRoot $ProjRoot

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
Save (New-BucketIcon $fluidRgb[0] $fluidRgb[1] $fluidRgb[2]) `
    (Join-Path $ItemDir 'rich_tungsten_concentrate_slurry_bucket.png')
Write-FlatItemModel 'rich_tungsten_concentrate_slurry_bucket'

Write-Host 'Done: tungsten items + 3 blocks + fluid/bucket.'

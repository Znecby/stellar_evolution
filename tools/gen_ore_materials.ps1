﻿﻿﻿# gen_ore_materials.ps1 -- Ore processing line (G line) item icons / fluid textures / buckets.
#
# Items:
#   * dusts               -> material template tint (material_dust.png)
#   * concentrate sands   -> vanilla prismarine_crystals recolored per metal
#   * raw_uranium         -> vanilla raw_iron recolored
#   * uranium_ingot       -> vanilla iron_ingot recolored
# Fluids: vanilla water animation strip recolored (tools/lib_water_fluid.ps1).
# Buckets: vanilla water bucket template recolored.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen_ore_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$BlockDir  = Join-Path $TexRoot 'block'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$BlockModels = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\block'
$Blockstates = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\blockstates'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
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

function Is-OrePixel($c) {
    if ($c.A -eq 0) { return $false }
    return (($c.R - $c.B) -ge 16) -and (($c.R - $c.G) -ge 6)
}
function Lerp3($a, $b, [double]$t) {
    return @(
        [int]([Math]::Round($a[0] + ($b[0] - $a[0]) * $t)),
        [int]([Math]::Round($a[1] + ($b[1] - $a[1]) * $t)),
        [int]([Math]::Round($a[2] + ($b[2] - $a[2]) * $t))
    )
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
function New-OreBlock([string]$entry, $light, $body, $dark) {
    $bytes = Read-JarEntryBytes $Jar $entry
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    $src = [System.Drawing.Bitmap]::FromStream($ms)
    $out = Convert-Ore $src $light $body $dark
    $src.Dispose(); $ms.Dispose()
    return $out
}

# 通用“差异矿”换色：以底岩为底，只把与底岩不同的矿脉像素换成目标色。
# 用于下界石英矿、煤矿、金矿等不能靠 Is-OrePixel 判断的模板。
function New-DiffOreBlock([string]$oreEntry, [string]$stoneEntry, $light, $body, $dark) {
    $oreBytes = Read-JarEntryBytes $Jar $oreEntry
    $stoneBytes = Read-JarEntryBytes $Jar $stoneEntry
    $oreMs = New-Object System.IO.MemoryStream(,$oreBytes)
    $stoneMs = New-Object System.IO.MemoryStream(,$stoneBytes)
    $ore = [System.Drawing.Bitmap]::FromStream($oreMs)
    $stone = [System.Drawing.Bitmap]::FromStream($stoneMs)

    $min = 255.0; $max = 0.0
    for ($y = 0; $y -lt $ore.Height; $y++) {
        for ($x = 0; $x -lt $ore.Width; $x++) {
            $c = $ore.GetPixel($x, $y)
            $s = $stone.GetPixel($x, $y)
            $diff = [Math]::Abs($c.R - $s.R) + [Math]::Abs($c.G - $s.G) + [Math]::Abs($c.B - $s.B)
            if ($c.A -eq 0 -or $diff -le 24) { continue }
            $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
            if ($lum -lt $min) { $min = $lum }
            if ($lum -gt $max) { $max = $lum }
        }
    }
    $span = [Math]::Max(1.0, ($max - $min))

    $bmp = New-Bmp $ore.Width $ore.Height
    for ($y = 0; $y -lt $ore.Height; $y++) {
        for ($x = 0; $x -lt $ore.Width; $x++) {
            $c = $ore.GetPixel($x, $y)
            $s = $stone.GetPixel($x, $y)
            $diff = [Math]::Abs($c.R - $s.R) + [Math]::Abs($c.G - $s.G) + [Math]::Abs($c.B - $s.B)
            if ($c.A -eq 0 -or $diff -le 24) {
                $bmp.SetPixel($x, $y, $s)
                continue
            }
            $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
            $t = [Math]::Min(1.0, [Math]::Max(0.0, (($lum - $min) / $span)))
            if ($t -lt 0.5) { $rgb = Lerp3 $dark $body ($t * 2.0) }
            else { $rgb = Lerp3 $body $light (($t - 0.5) * 2.0) }
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rgb[0], $rgb[1], $rgb[2]))
        }
    }

    $ore.Dispose(); $stone.Dispose(); $oreMs.Dispose(); $stoneMs.Dispose()
    return $bmp
}

# 按原版矿石「真实结构」重绘：石头底与矿脉内的灰色高光原样保留，只给矿色像素换色。
#   mode = 'dark'  ：煤矿石模板，亮度 < 95 的暗像素才算矿脉（灰高光 92~143 保留）
#   mode = 'chroma'：金矿石模板，RGB 色差 >= 24 的彩色像素才算矿脉（灰高光 92~162 保留）
function Is-VeinPixel($c, [string]$mode) {
    if ($c.A -eq 0) { return $false }
    if ($mode -eq 'dark') {
        $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
        return ($lum -lt 95)
    }
    if ($mode -eq 'deep-dark') {
        # 深板岩模板：深板岩底最暗 47,47,55，矿脉黑 10~42
        $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
        return ($lum -lt 45)
    }
    $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
    $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
    return (($mx - $mn) -ge 24)
}
function New-StructureOreBlock([string]$oreEntry, [string]$stoneEntry, [string]$mode, $light, $body, $dark) {
    $oreBytes = Read-JarEntryBytes $Jar $oreEntry
    $stoneBytes = Read-JarEntryBytes $Jar $stoneEntry
    $oreMs = New-Object System.IO.MemoryStream(,$oreBytes)
    $stoneMs = New-Object System.IO.MemoryStream(,$stoneBytes)
    $ore = [System.Drawing.Bitmap]::FromStream($oreMs)
    $stone = [System.Drawing.Bitmap]::FromStream($stoneMs)

    $min = 255.0; $max = 0.0
    for ($y = 0; $y -lt $ore.Height; $y++) {
        for ($x = 0; $x -lt $ore.Width; $x++) {
            $c = $ore.GetPixel($x, $y)
            if (-not (Is-VeinPixel $c $mode)) { continue }
            $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
            if ($lum -lt $min) { $min = $lum }
            if ($lum -gt $max) { $max = $lum }
        }
    }
    $span = [Math]::Max(1.0, ($max - $min))

    $bmp = New-Bmp $ore.Width $ore.Height
    for ($y = 0; $y -lt $ore.Height; $y++) {
        for ($x = 0; $x -lt $ore.Width; $x++) {
            $c = $ore.GetPixel($x, $y)
            if (-not (Is-VeinPixel $c $mode)) {
                $s = $stone.GetPixel($x, $y)
                $diff = [Math]::Abs($c.R - $s.R) + [Math]::Abs($c.G - $s.G) + [Math]::Abs($c.B - $s.B)
                if ($diff -eq 0) { $bmp.SetPixel($x, $y, $s) } else { $bmp.SetPixel($x, $y, $c) }
                continue
            }
            $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
            $t = [Math]::Min(1.0, [Math]::Max(0.0, (($lum - $min) / $span)))
            if ($t -lt 0.5) { $rgb = Lerp3 $dark $body ($t * 2.0) }
            else { $rgb = Lerp3 $body $light (($t - 0.5) * 2.0) }
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rgb[0], $rgb[1], $rgb[2]))
        }
    }

    $ore.Dispose(); $stone.Dispose(); $oreMs.Dispose(); $stoneMs.Dispose()
    return $bmp
}

# ==================================================================== 1) material template tint (dusts)
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

# ==================================================================== 2) vanilla texture recolor
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
    $src.Dispose()
    $ms.Dispose()
    return $out
}

# ==================================================================== 3) item list
$dusts = @(
    @{ id = 'iron_dust';    rgb = @(190, 190, 196) },
    @{ id = 'gold_dust';    rgb = @(242, 202,  74) },
    @{ id = 'uranium_dust'; rgb = @(126, 204, 112) },
    @{ id = 'debris_dust';  rgb = @(122,  96,  86) },
    @{ id = 'diamond_dust'; rgb = @(130, 228, 232) },
    @{ id = 'emerald_dust'; rgb = @( 64, 212, 112) },
    @{ id = 'lapis_dust';   rgb = @( 62,  94, 214) },
    @{ id = 'quartz_dust';  rgb = @(238, 232, 222) },
    @{ id = 'graphite_dust'; rgb = @( 58,  58,  62) },
    @{ id = 'beryllium_dust'; rgb = @(170, 205, 190) },
    @{ id = 'borax';          rgb = @(228, 235, 242) },
    @{ id = 'boron_dust';     rgb = @(92,  74,  58) },
    @{ id = 'sodium_dust';    rgb = @(214, 214, 220) },
    @{ id = 'sodium_chloride'; rgb = @(238, 238, 244) },
    @{ id = 'sodium_hydroxide'; rgb = @(226, 232, 238) },
    @{ id = 'vanadium_dust';  rgb = @(150, 160, 170) },
    @{ id = 'gallium_dust';   rgb = @(150, 170, 200) },
    @{ id = 'magnesium_dust'; rgb = @(200, 205, 210) },
    @{ id = 'silicon_dust';   rgb = @( 90,  95, 100) }
)

$sands = @(
    @{ id = 'iron_concentrate_sand';      rgb = @(150, 145, 140) },
    @{ id = 'copper_concentrate_sand';    rgb = @(200, 120,  70) },
    @{ id = 'gold_concentrate_sand';      rgb = @(230, 190,  70) },
    @{ id = 'uranium_concentrate_sand';   rgb = @(120, 200, 110) },
    @{ id = 'debris_concentrate_sand';    rgb = @(110,  86,  80) },
    @{ id = 'coal_concentrate_sand';      rgb = @( 48,  48,  52) },
    @{ id = 'diamond_concentrate_sand';   rgb = @(110, 220, 225) },
    @{ id = 'emerald_concentrate_sand';   rgb = @( 60, 200, 110) },
    @{ id = 'lapis_concentrate_sand';     rgb = @( 60,  90, 200) },
    @{ id = 'redstone_concentrate_sand';  rgb = @(200,  40,  40) },
    @{ id = 'quartz_concentrate_sand';    rgb = @(235, 230, 220) }
)

# ---- B2 独居石 / B3 铝土矿：重砂（海晶砂换色）与粉 / 锭（材料模板）
$monaziteSands = @(
    @{ id = 'monazite_sand'; rgb = @(210, 190, 120) },
    @{ id = 'zircon_sand';   rgb = @(210, 215, 225) },
    @{ id = 'ilmenite_sand'; rgb = @( 95, 100, 112) }
)
$monaziteItems = @(
    @{ id = 'monazite_concentrate';   shape = 'dust';  rgb = @(190, 170, 90) },
    @{ id = 'phosphate';              shape = 'dust';  rgb = @(220, 215, 190) },
    @{ id = 'thorium_concentrate';    shape = 'dust';  rgb = @(120, 140, 120) },
    @{ id = 'rare_earth_chloride';    shape = 'dust';  rgb = @(200, 195, 180) },
    @{ id = 'crude_zirconium_powder'; shape = 'dust';  rgb = @(150, 158, 172) },
    @{ id = 'hafnium_powder';         shape = 'dust';  rgb = @(120, 128, 142) },
    @{ id = 'zircaloy_powder';        shape = 'dust';  rgb = @(142, 152, 166) },
    @{ id = 'zircaloy_ingot';         shape = 'ingot'; rgb = @(150, 160, 174) },
    @{ id = 'tin_dust';               shape = 'dust';  rgb = @(190, 195, 205) },
    @{ id = 'neodymium_dust';         shape = 'dust';  rgb = @(180, 155, 210) },
    @{ id = 'samarium_dust';          shape = 'dust';  rgb = @(200, 185, 140) },
    @{ id = 'gadolinium_dust';        shape = 'dust';  rgb = @(160, 175, 190) },
    @{ id = 'cerium_dust';            shape = 'dust';  rgb = @(205, 200, 185) },
    @{ id = 'ferrotitanium_powder';   shape = 'dust';  rgb = @(118, 120, 130) },
    @{ id = 'rutile_powder';          shape = 'dust';  rgb = @(150,  82,  66) }
)
$bauxiteItems = @(
    @{ id = 'bauxite_powder'; shape = 'dust'; rgb = @(200, 185, 170) },
    @{ id = 'alumina';        shape = 'dust'; rgb = @(240, 238, 232) },
    @{ id = 'cryolite';       shape = 'dust'; rgb = @(215, 230, 240) },
    @{ id = 'red_mud';        shape = 'dust'; rgb = @(150,  70,  50) }
)
$oreBlocks = @(
    @{ id = 'monazite_ore';          src = 'assets/minecraft/textures/block/iron_ore.png';           light = @(220, 205, 140); body = @(170, 155, 90);  dark = @(100, 85, 45) },
    @{ id = 'deepslate_monazite_ore'; src = 'assets/minecraft/textures/block/deepslate_iron_ore.png';  light = @(220, 205, 140); body = @(170, 155, 90);  dark = @(100, 85, 45) },
    @{ id = 'bauxite_ore';            src = 'assets/minecraft/textures/block/iron_ore.png';           light = @(235, 225, 215); body = @(195, 180, 165); dark = @(130, 115, 100) },
    @{ id = 'deepslate_bauxite_ore';  src = 'assets/minecraft/textures/block/deepslate_iron_ore.png';  light = @(235, 225, 215); body = @(195, 180, 165); dark = @(130, 115, 100) }
)

foreach ($it in $dusts) {
    Save (New-TintedItem 'dust' $it.rgb[0] $it.rgb[1] $it.rgb[2]) (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (dust template)" -f $it.id)
}

foreach ($it in $sands) {
    Save (New-RecoloredVanilla 'assets/minecraft/textures/item/prismarine_crystals.png' `
            $it.rgb[0] $it.rgb[1] $it.rgb[2]) (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (prismarine crystals recolored)" -f $it.id)
}

# ---- B2 / B3 物品与矿石方块
foreach ($it in $monaziteSands) {
    Save (New-RecoloredVanilla 'assets/minecraft/textures/item/prismarine_crystals.png' `
            $it.rgb[0] $it.rgb[1] $it.rgb[2]) (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (prismarine crystals recolored)" -f $it.id)
}
foreach ($it in $monaziteItems + $bauxiteItems) {
    Save (New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]) `
        (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (template={1})" -f $it.id, $it.shape)
}
foreach ($b in $oreBlocks) {
    Save (New-OreBlock $b.src $b.light $b.body $b.dark) (Join-Path $BlockDir ("{0}.png" -f $b.id))
    Write-BlockModel $b.id
    Write-Host ("  {0}.json (ore block)" -f $b.id)
}

# 下界硫磺矿：netherrack 底 + 石英矿脉换色（不能复用 Is-OrePixel）
Save (New-DiffOreBlock 'assets/minecraft/textures/block/nether_quartz_ore.png' `
        'assets/minecraft/textures/block/netherrack.png' `
        @(255, 240, 120) @(220, 190, 60) @(140, 110, 30)) `
    (Join-Path $BlockDir 'sulfur_ore.png')
Write-BlockModel 'sulfur_ore'
Write-Host '  sulfur_ore.json (netherrack + quartz recolored)'

# 月球钛铁矿：初版口径——铁矿模板换色（钛灰蓝矿脉）
Save (New-OreBlock 'assets/minecraft/textures/block/iron_ore.png' `
        @(180, 180, 192) @(92, 92, 108) @(34, 34, 46)) `
    (Join-Path $BlockDir 'moon_ilmenite_ore.png')
Write-BlockModel 'moon_ilmenite_ore'
Write-Host '  moon_ilmenite_ore.png (iron ore template recolored)'

# 月球冰岩矿：金矿石模板 → 冰蓝矿脉；灰色高光原样保留
Save (New-StructureOreBlock 'assets/minecraft/textures/block/gold_ore.png' `
        'assets/minecraft/textures/block/stone.png' 'chroma' `
        @(238, 252, 255) @(170, 220, 238) @(82, 148, 182)) `
    (Join-Path $BlockDir 'moon_ice_rock_ore.png')
Write-BlockModel 'moon_ice_rock_ore'
Write-Host '  moon_ice_rock_ore.png (gold ore structure, grays kept)'

# 月球辉钛晶骸：紫水晶块整体换色（#8A2BE2）
Save (New-RecoloredVanilla 'assets/minecraft/textures/block/amethyst_block.png' 138 43 226) `
    (Join-Path $BlockDir 'moon_glow_titanium_ore.png')
Write-BlockModel 'moon_glow_titanium_ore'
Write-Host '  moon_glow_titanium_ore.png (amethyst block recolored)'

# 月球深板岩矿石：深板岩底 + 对应矿脉换色
Save (New-OreBlock 'assets/minecraft/textures/block/deepslate_iron_ore.png' `
        @(104, 106, 122) @(58, 60, 74) @(20, 20, 28)) `
    (Join-Path $BlockDir 'deepslate_moon_ilmenite_ore.png')
Write-BlockModel 'deepslate_moon_ilmenite_ore'
Write-Host '  deepslate_moon_ilmenite_ore.png (deepslate iron ore recolored)'

Save (New-StructureOreBlock 'assets/minecraft/textures/block/deepslate_gold_ore.png' `
        'assets/minecraft/textures/block/deepslate.png' 'chroma' `
        @(238, 252, 255) @(170, 220, 238) @(82, 148, 182)) `
    (Join-Path $BlockDir 'deepslate_moon_ice_rock_ore.png')
Write-BlockModel 'deepslate_moon_ice_rock_ore'
Write-Host '  deepslate_moon_ice_rock_ore.png (deepslate gold ore structure)'

# raw uranium / uranium ingot
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 120 200 110) `
    (Join-Path $ItemDir 'raw_uranium.png')
Write-FlatItemModel 'raw_uranium'
Write-Host '  raw_uranium.json (raw iron recolored)'

# raw monazite / raw bauxite（与其他金属矿的粗矿同口径）
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 200 178 110) `
    (Join-Path $ItemDir 'raw_monazite.png')
Write-FlatItemModel 'raw_monazite'
Write-Host '  raw_monazite.json (raw iron recolored)'

Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 205 190 175) `
    (Join-Path $ItemDir 'raw_bauxite.png')
Write-FlatItemModel 'raw_bauxite'
Write-Host '  raw_bauxite.json (raw iron recolored)'

# 硫磺晶体（下界石英换色）
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/quartz.png' 245 215 90) `
    (Join-Path $ItemDir 'sulfur_crystal.png')
Write-FlatItemModel 'sulfur_crystal'
Write-Host '  sulfur_crystal.json (nether quartz recolored)'

# 月球新物品：粗钛铁矿 / 斜长岩 / 辉钛晶体
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 110 110 128) `
    (Join-Path $ItemDir 'raw_ilmenite.png')
Write-FlatItemModel 'raw_ilmenite'
Write-Host '  raw_ilmenite.json (raw iron recolored)'

Save (New-RecoloredVanilla 'assets/minecraft/textures/item/raw_iron.png' 200 200 206) `
    (Join-Path $ItemDir 'anorthosite.png')
Write-FlatItemModel 'anorthosite'
Write-Host '  anorthosite.json (raw iron recolored)'

Save (New-RecoloredVanilla 'assets/minecraft/textures/item/amethyst_shard.png' 138 43 226) `
    (Join-Path $ItemDir 'glow_titanium_crystal.png')
Write-FlatItemModel 'glow_titanium_crystal'
Write-Host '  glow_titanium_crystal.json (amethyst shard recolored)'

Save (New-RecoloredVanilla 'assets/minecraft/textures/item/iron_ingot.png' 130 205 120) `
    (Join-Path $ItemDir 'uranium_ingot.png')
Write-FlatItemModel 'uranium_ingot'
Write-Host '  uranium_ingot.json (iron ingot recolored)'

# ==================================================================== 4) fluids + buckets
$fluids = @(
    @{ name = 'rich_iron_concentrate_slurry';      rgb = @(150, 130, 110) },
    @{ name = 'rich_copper_concentrate_slurry';    rgb = @(190, 110,  60) },
    @{ name = 'rich_gold_concentrate_slurry';      rgb = @(220, 180,  60) },
    @{ name = 'rich_uranium_concentrate_slurry';   rgb = @(110, 190, 100) },
    @{ name = 'rich_debris_concentrate_slurry';    rgb = @(100,  80,  75) },
    @{ name = 'rich_coal_concentrate_slurry';      rgb = @( 45,  45,  50) },
    @{ name = 'rich_diamond_concentrate_slurry';   rgb = @(100, 215, 220) },
    @{ name = 'rich_emerald_concentrate_slurry';   rgb = @( 55, 195, 105) },
    @{ name = 'rich_lapis_concentrate_slurry';     rgb = @( 55,  85, 200) },
    @{ name = 'rich_redstone_concentrate_slurry';  rgb = @(195,  35,  35) },
    @{ name = 'rich_quartz_concentrate_slurry';    rgb = @(230, 225, 215) },
    @{ name = 'hydrogen_peroxide';                 rgb = @(190, 225, 235) },
    @{ name = 'sulfuric_acid';                     rgb = @(225, 205, 110) },
    @{ name = 'chlorine';                          rgb = @(205, 225, 105) },
    @{ name = 'titanium_tetrachloride';            rgb = @(190, 200, 205) },
    @{ name = 'mercury';                           rgb = @(178, 184, 192) },
    @{ name = 'hydrochloric_acid';                 rgb = @(185, 200, 120) },
    @{ name = 'boric_acid';                        rgb = @(200, 225, 218) }
)

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

foreach ($f in $fluids) {
    $r = [int]$f.rgb[0]; $g = [int]$f.rgb[1]; $bl = [int]$f.rgb[2]
    Write-WaterFluidTextures -BlockDir $BlockDir -Name $f.name -Rgb @($r, $g, $bl) -ProjRoot $ProjRoot

    $bucket = "{0}_bucket" -f $f.name
    Save (New-BucketIcon $r $g $bl) (Join-Path $ItemDir ("{0}.png" -f $bucket))
    Write-FlatItemModel $bucket
}

Write-Host ("Done: {0} dusts, {1} sands, raw uranium + uranium ingot, {2} fluids + buckets." -f `
    $dusts.Count, $sands.Count, $fluids.Count)

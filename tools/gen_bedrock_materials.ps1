﻿﻿﻿# gen_bedrock_materials.ps1 -- Bedrock infinite resource line item / fluid assets.
#
# Items:
#   bedrock_ore              -> prismarine shard recolored (fragmented bedrock chunk)
#   bedrock_powder           -> material dust template tint (milled bedrock powder)
#   activated_bedrock_powder -> quartz recolored (high-vibration bedrock crystal)
#   bedrock_residue          -> clay ball recolored (leached bedrock precipitate)
#   mixed_sulfide            -> material dust template tint
# Fluids: vanilla water strip recolored + bucket icon per entry.
#
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen_bedrock_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$BlockDir  = Join-Path $TexRoot 'block'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
$BucketTemplate = Join-Path $PSScriptRoot 'templates\water_bucket.png'
New-Item -ItemType Directory -Force -Path $ItemDir, $BlockDir, $ModelsDir, $ItemsDir | Out-Null
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
    $src.Dispose()
    $ms.Dispose()
    return $out
}

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

# ---- items
$items = @(
    @{ id = 'bedrock_powder';  shape = 'dust'; rgb = @( 92,  92, 100) },
    @{ id = 'mixed_sulfide';   shape = 'dust'; rgb = @(120, 110, 100) }
)
foreach ($it in $items) {
    Save (New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]) (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json (dust template)" -f $it.id)
}

# 碎基岩块状物：海晶碎片换色
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/prismarine_shard.png' 88 88 96) `
    (Join-Path $ItemDir 'bedrock_ore.png')
Write-FlatItemModel 'bedrock_ore'
Write-Host '  bedrock_ore.json (prismarine shard recolored)'

# 高振基岩晶态物：石英换色
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/quartz.png' 120 230 200) `
    (Join-Path $ItemDir 'activated_bedrock_powder.png')
Write-FlatItemModel 'activated_bedrock_powder'
Write-Host '  activated_bedrock_powder.json (quartz recolored)'

# 浸出基岩沉淀物：黏土球换色
Save (New-RecoloredVanilla 'assets/minecraft/textures/item/clay_ball.png' 120 100 78) `
    (Join-Path $ItemDir 'bedrock_residue.png')
Write-FlatItemModel 'bedrock_residue'
Write-Host '  bedrock_residue.json (clay ball recolored)'

# ---- fluids + buckets
$fluids = @(
    @{ name = 'gamma_solvent';          rgb = @(120, 220, 140) },
    @{ name = 'halide_solvent';         rgb = @(200, 180,  90) },
    @{ name = 'selfheat_solvent';       rgb = @(230, 120,  60) },
    @{ name = 'chelate_solvent';        rgb = @(160, 100, 200) },
    @{ name = 'volatile_halide_mix';    rgb = @(190, 210, 220) },
    @{ name = 'molten_salt_leachate';   rgb = @(220, 140,  70) },
    @{ name = 'loaded_organic';         rgb = @(120,  90,  60) },
    @{ name = 'bioleachate';            rgb = @(140, 200, 120) }
)
foreach ($f in $fluids) {
    $r = [int]$f.rgb[0]; $g = [int]$f.rgb[1]; $bl = [int]$f.rgb[2]
    Write-WaterFluidTextures -BlockDir $BlockDir -Name $f.name -Rgb @($r, $g, $bl) -ProjRoot $ProjRoot

    $bucket = "{0}_bucket" -f $f.name
    Save (New-BucketIcon $r $g $bl) (Join-Path $ItemDir ("{0}.png" -f $bucket))
    Write-FlatItemModel $bucket
}

Write-Host ("Done: 5 items + {0} fluids + buckets." -f $fluids.Count)

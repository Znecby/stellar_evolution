# gen_air_materials.ps1 -- air separation line assets.
# - fluid textures (air / nitrogen) from vanilla water animation strip
# - bucket icons + item models/definitions
# - stainless steel grate texture recolored from vanilla copper grate
# Usage: powershell -ExecutionPolicy Bypass -File tools/gen_air_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$BlockDir  = Join-Path $TexRoot 'block'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$BucketTemplate = Join-Path $ProjRoot 'tools\templates\water_bucket.png'
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
    Write-Host ("  wrote {0}" -f (Split-Path -Leaf $path))
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

# ------------------------------------------------------------------ 1) fluids
$fluids = @(
    @{ name = 'air';      rgb = @(200, 225, 240) },
    @{ name = 'nitrogen'; rgb = @(150, 200, 230) },
    @{ name = 'argon';    rgb = @(170, 150, 220) }
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

# ------------------------------------------------------------------ 2) steel grate
$jar = Get-VanillaJarPath $ProjRoot
$grateBytes = Read-JarEntryBytes $jar 'assets/minecraft/textures/block/copper_grate.png'
$srcStream = New-Object System.IO.MemoryStream(,$grateBytes)
$srcGrate = [System.Drawing.Bitmap]::FromStream($srcStream)
$w = $srcGrate.Width; $h = $srcGrate.Height

$minL = 255.0; $maxL = 0.0
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $c = $srcGrate.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        $l = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
        if ($l -lt $minL) { $minL = $l }
        if ($l -gt $maxL) { $maxL = $l }
    }
}
$span = [Math]::Max(1.0, $maxL - $minL)

$grate = New-Bmp $w $h
for ($y = 0; $y -lt $h; $y++) {
    for ($x = 0; $x -lt $w; $x++) {
        $c = $srcGrate.GetPixel($x, $y)
        if ($c.A -eq 0) {
            # fill the see-through holes with a dark metal shadow so the block stays opaque
            Px $grate $x $y (Col 38 42 46 255)
            continue
        }
        $l = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
        $t = ($l - $minL) / $span
        $k = 0.55 + 0.90 * $t
        Px $grate $x $y (Col ([int](198 * $k)) ([int](204 * $k)) ([int](212 * $k)) 255)
    }
}
$srcGrate.Dispose()
$srcStream.Dispose()
Save $grate (Join-Path $BlockDir 'steel_grate.png')

Write-Host ("done: {0} fluids + steel grate" -f $fluids.Count)

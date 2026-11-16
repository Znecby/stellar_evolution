Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot '..\src\main\resources\assets\stellar_evolution\textures\item'
New-Item -ItemType Directory -Force -Path $out | Out-Null

function C([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }

function Shade([string]$hex, [int]$delta) {
    $c = C $hex
    $r = [Math]::Max(0, [Math]::Min(255, $c.R + $delta))
    $g = [Math]::Max(0, [Math]::Min(255, $c.G + $delta))
    $b = [Math]::Max(0, [Math]::Min(255, $c.B + $delta))
    return [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
}

function Px($bmp, [int]$x, [int]$y, [string]$hex) {
    if ($x -ge 0 -and $x -lt 16 -and $y -ge 0 -and $y -lt 16) {
        $bmp.SetPixel($x, $y, (C $hex))
    }
}

function Fill($g, [int]$x, [int]$y, [int]$w, [int]$h, [string]$hex) {
    $b = [System.Drawing.SolidBrush]::new((C $hex))
    $g.FillRectangle($b, $x, $y, $w, $h)
    $b.Dispose()
}

function Stroke($g, [int]$x, [int]$y, [int]$w, [int]$h, [string]$hex) {
    $p = [System.Drawing.Pen]::new((C $hex), 1)
    $g.DrawRectangle($p, $x, $y, $w - 1, $h - 1)
    $p.Dispose()
}

function Line($g, [int]$x1, [int]$y1, [int]$x2, [int]$y2, [string]$hex) {
    $p = [System.Drawing.Pen]::new((C $hex), 1)
    $g.DrawLine($p, $x1, $y1, $x2, $y2)
    $p.Dispose()
}

function Ellipse($g, [int]$x, [int]$y, [int]$w, [int]$h, [string]$fill, [string]$stroke = $null) {
    $b = [System.Drawing.SolidBrush]::new((C $fill))
    $g.FillEllipse($b, $x, $y, $w, $h)
    $b.Dispose()
    if ($stroke) {
        $p = [System.Drawing.Pen]::new((C $stroke), 1)
        $g.DrawEllipse($p, $x, $y, $w - 1, $h - 1)
        $p.Dispose()
    }
}

# ---------------------------------------------------------------- shared palette block
# Each 4x4 tile is one flat material: 1px lit edge, 1px shaded edge, sparse speckles.
function Block($bmp, [int]$x, [int]$y, [string]$hex, [bool]$plain = $false) {
    $base = C $hex
    $light = Shade $hex 24
    $dark = Shade $hex -26
    for ($dy = 0; $dy -lt 4; $dy++) {
        for ($dx = 0; $dx -lt 4; $dx++) {
            $col = $base
            if (-not $plain) {
                if ($dy -eq 0 -or $dx -eq 0) { $col = $light }
                elseif ($dy -eq 3 -or $dx -eq 3) { $col = $dark }
                if ((($dx * 7 + $dy * 5 + $x * 3 + $y) % 11) -eq 0) { $col = Shade $hex -13 }
                if ((($dx * 5 + $dy * 3 + $x + $y) % 17) -eq 0) { $col = Shade $hex 13 }
            }
            $bmp.SetPixel($x + $dx, $y + $dy, $col)
        }
    }
}

function Hazard($bmp, [int]$x, [int]$y) {
    for ($dy = 0; $dy -lt 4; $dy++) {
        for ($dx = 0; $dx -lt 4; $dx++) {
            $col = if ((($dx + $dy) % 4) -lt 2) { C '#E8C84A' } else { C '#23272B' }
            $bmp.SetPixel($x + $dx, $y + $dy, $col)
        }
    }
}

# 16x4 bottom strip: dark plate, panel seams and rivets.
function Plate($bmp, [int]$y, [string]$hex, [string]$edge) {
    for ($dy = 0; $dy -lt 4; $dy++) {
        for ($dx = 0; $dx -lt 16; $dx++) {
            $col = C $hex
            if ($dy -eq 0) { $col = C $edge }
            if ($dy -eq 3) { $col = Shade $hex -22 }
            if ($dx % 5 -eq 0) { $col = Shade $hex -16 }
            $bmp.SetPixel($dx, $y + $dy, $col)
        }
    }
    Px $bmp 1 ($y + 2) '#DCE6EC'
    Px $bmp 6 ($y + 2) '#DCE6EC'
    Px $bmp 11 ($y + 2) '#DCE6EC'
    Px $bmp 14 ($y + 2) '#DCE6EC'
}

function Save-Bmp($bmp, [string]$name) {
    $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Layout (16x16):
#   0,0 light   4,0 steel   8,0 dark    12,0 black
#   0,4 copper  4,4 brass   8,4 green   12,4 white
#   0,8 hazard  4,8 glass   8,8 red     12,8 highlight
#   0,12.. 16x4 plate strip
function New-Palette([string]$name, [hashtable]$over) {
    $c = @{
        light = '#D2DBE1'; steel = '#8B979F'; dark = '#4B565E'; black = '#2A3138'
        copper = '#C97B4C'; brass = '#D8B84A'; green = '#6FC24E'; white = '#E9EFF4'
        glass = '#8FD8F0'; red = '#C0463C'; highlight = '#F4FAFF'
    }
    foreach ($k in $over.Keys) { $c[$k] = $over[$k] }
    $bmp = [System.Drawing.Bitmap]::new(16, 16)
    Block $bmp 0 0 $c.light
    Block $bmp 4 0 $c.steel
    Block $bmp 8 0 $c.dark
    Block $bmp 12 0 $c.black
    Block $bmp 0 4 $c.copper
    Block $bmp 4 4 $c.brass
    Block $bmp 8 4 $c.green
    Block $bmp 12 4 $c.white
    Hazard $bmp 0 8
    Block $bmp 4 8 $c.glass
    Block $bmp 8 8 $c.red
    Block $bmp 12 8 $c.highlight
    Plate $bmp 12 $c.dark $c.steel
    Save-Bmp $bmp $name
}

# ---------------------------------------------------------------- quartz glass (3D pane stack)
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.Clear([System.Drawing.Color]::Transparent)
Stroke $g 1 1 14 14 '#6FB6CC'
Stroke $g 2 2 12 12 '#CDEFF8'
Stroke $g 3 3 10 10 '#EAFBFF'
Fill $g 1 1 2 2 '#EAFBFF'
Fill $g 13 1 2 2 '#CDEFF8'
Fill $g 1 13 2 2 '#CDEFF8'
Fill $g 13 13 2 2 '#6FB6CC'
Line $g 4 12 11 5 '#FFFFFF'
Line $g 5 12 12 5 '#E8FFFF'
Line $g 4 10 8 6 '#EAFBFF'
Px $bmp 7 7 '#FFFFFF'
Px $bmp 10 9 '#EAFBFF'
Px $bmp 6 4 '#CDEFF8'
$g.Dispose()
Save-Bmp $bmp 'quartz_glass'

# ---------------------------------------------------------------- tempered glass (3D thick pane)
$bmp = [System.Drawing.Bitmap]::new(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.Clear([System.Drawing.Color]::Transparent)
Stroke $g 1 1 14 14 '#1E2E36'
Stroke $g 2 2 12 12 '#8FD8F0'
Stroke $g 3 3 10 10 '#4E7E96'
for ($i = 5; $i -lt 12; $i += 2) {
    Px $bmp $i 2 '#C8F2FF'
    Px $bmp $i 13 '#A8DCEA'
    Px $bmp 2 $i '#C8F2FF'
    Px $bmp 13 $i '#A8DCEA'
}
Line $g 5 12 12 5 '#FFFFFF'
Px $bmp 5 5 '#EAFBFF'
Px $bmp 10 10 '#EAFBFF'
Px $bmp 7 9 '#FFFFFF'
Fill $g 10 10 4 4 '#C9D2D8'
Stroke $g 10 10 4 4 '#3A7E96'
Px $bmp 11 11 '#3A7E96'
Px $bmp 12 11 '#8FD8F0'
Px $bmp 11 12 '#8FD8F0'
Px $bmp 12 12 '#3A7E96'
$g.Dispose()
Save-Bmp $bmp 'tempered_glass'

# ---------------------------------------------------------------- 8 advanced components (3D model palettes)
New-Palette 'corrosion_liner' @{
    light = '#D9E3E9'; steel = '#93A0A8'; dark = '#46525A'; black = '#252C31'
    green = '#7ED158'; white = '#F2F7F9'; copper = '#B9744A'
}
New-Palette 'radiation_shielding' @{
    light = '#BAC4CA'; steel = '#79858D'; dark = '#3A444C'; black = '#1D2328'
    copper = '#B9744A'; white = '#E3E9EE'; brass = '#D6B84E'
}
New-Palette 'centrifuge_rotor' @{
    light = '#DDE7ED'; steel = '#8E9AA2'; dark = '#45515A'; black = '#232A30'
    copper = '#C98950'; brass = '#D9BE5C'
}
New-Palette 'fuel_loading_head' @{
    light = '#D3DCE2'; steel = '#8B979F'; dark = '#4B565E'; black = '#262E34'
    copper = '#D07A4A'; brass = '#E3C55B'; green = '#79D25B'
}
New-Palette 'reflector_module' @{
    light = '#C6D0D6'; steel = '#7E8A92'; dark = '#333B42'; black = '#1B2126'
    white = '#F6FAFD'; glass = '#BFE6F2'; brass = '#CBB55A'
}
New-Palette 'control_rod_drive' @{
    light = '#CBD5DB'; steel = '#89959D'; dark = '#414B53'; black = '#20262B'
    copper = '#C87C4E'; green = '#79D25B'; brass = '#D4B94F'
}
New-Palette 'turbine_blade' @{
    light = '#E4EEF4'; steel = '#9AA7AF'; dark = '#4E5A63'; black = '#252C31'
    copper = '#D08A54'; highlight = '#FFFFFF'
}
New-Palette 'steam_valve_manifold' @{
    light = '#D8E2E8'; steel = '#8F9BA3'; dark = '#4A555D'; black = '#232A30'
    copper = '#CE7F50'; glass = '#9FE0F4'; brass = '#DCC05A'
}

Write-Output "generated 2 flat materials + 8 component palettes"

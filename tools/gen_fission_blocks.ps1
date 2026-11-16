# gen_fission_blocks.ps1 —— 生成核裂变线矿石贴图（换色重绘原版铁矿）。
#
# 口径（2026-10-23 改版，用户指定）：
#   · 直接取原版 `iron_ore.png` / `deepslate_iron_ore.png` 作为底稿 —— 石底噪点、矿斑形状、
#     高光与暗边全部照原版矿石，**只把铁锈色换色成铀的黄绿色**；
#   · 换色按矿斑自身的亮度归一化映射到 暗 / 体 / 亮 三档铀色，保留原版的立体感，
#     不是简单叠加色相；
#   · 石底像素（灰阶）原样保留，深板岩版同理。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_fission_blocks.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot = Split-Path -Parent $PSScriptRoot
$BlockDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\block'
New-Item -ItemType Directory -Force -Path $BlockDir | Out-Null

# 铀色三档：亮 / 体 / 暗
$Light = @(158, 238, 112)
$Body  = @( 98, 182,  78)
$Dark  = @( 56, 112,  44)

function Lerp3($a, $b, [double]$t) {
    return @(
        [int]([Math]::Round($a[0] + ($b[0] - $a[0]) * $t)),
        [int]([Math]::Round($a[1] + ($b[1] - $a[1]) * $t)),
        [int]([Math]::Round($a[2] + ($b[2] - $a[2]) * $t))
    )
}

# 原版矿斑是暖色（R > B）；石底是灰阶（R≈G≈B）
function Is-OrePixel($c) {
    if ($c.A -eq 0) { return $false }
    return (($c.R - $c.B) -ge 16) -and (($c.R - $c.G) -ge 6)
}

function Convert-Ore($srcBmp) {
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
    $bmp = New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = $srcBmp.GetPixel($x, $y)
            if (Is-OrePixel $c) {
                $lum = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
                $t = (($lum - $min) / $span)
                if ($t -lt 0.5) { $rgb = Lerp3 $Dark $Body ($t * 2.0) }
                else { $rgb = Lerp3 $Body $Light (($t - 0.5) * 2.0) }
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rgb[0], $rgb[1], $rgb[2]))
            } else {
                $bmp.SetPixel($x, $y, $c)
            }
        }
    }
    return $bmp
}

# ---- 从构建产物里取原版贴图（不引入外部素材文件）
$jar = Get-ChildItem (Join-Path $ProjRoot 'build\moddev\artifacts') -Filter 'minecraft-patched-*-merged.jar' -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $jar) { throw '找不到 build\moddev\artifacts\minecraft-patched-*-merged.jar（先跑一次 gradlew build）' }

$zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
try {
    foreach ($pair in @(
            @{ entry = 'assets/minecraft/textures/block/iron_ore.png';           out = 'uranium_ore.png' },
            @{ entry = 'assets/minecraft/textures/block/deepslate_iron_ore.png'; out = 'deepslate_uranium_ore.png' })) {
        $e = $zip.GetEntry($pair.entry)
        if ($null -eq $e) { throw ("原版贴图缺项：{0}" -f $pair.entry) }
        $ms = New-Object System.IO.MemoryStream
        $s = $e.Open(); $s.CopyTo($ms); $s.Dispose(); $ms.Position = 0
        $src = [System.Drawing.Bitmap]::FromStream($ms)
        $bmp = Convert-Ore $src
        $src.Dispose(); $ms.Dispose()
        $bmp.Save((Join-Path $BlockDir $pair.out), [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        Write-Host ("  写出 {0}（换色自 {1}）" -f $pair.out, $pair.entry)
    }
} finally {
    $zip.Dispose()
}

Write-Host '完成：矿石贴图 2 张（原版铁矿换色）。'

# ---- 乏燃料池：HBM「Spent Fuel Pool Drum」的顶 / 侧贴图 → 基底（中性灰）换成本模组钨色
#   素材：tools/templates/hbm_waste_drum.png（顶）、hbm_waste_drum_side.png（侧）
#   来源：HBM's Nuclear Tech Mod 的 machine_waste_drum（英文名就是 Spent Fuel Pool Drum）。
#   换色只作用于「低饱和 + 中高亮度」的基底像素；黄色辐射标记与黑色底纹原样保留。
#   线性映射系数由「已确认的那版顶面」逐通道反推（out = a·L + b），保证与新生成的一致。
$topSrc = Join-Path $PSScriptRoot 'templates\hbm_waste_drum.png'
$sideSrc = Join-Path $PSScriptRoot 'templates\hbm_waste_drum_side.png'
foreach ($p in @($topSrc, $sideSrc)) {
    if (-not (Test-Path $p)) { throw ("缺少乏燃料池底稿：{0}" -f $p) }
}

function Convert-TungstenBase([System.Drawing.Bitmap]$src) {
    $out = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
            $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
            $sat = if ($mx -gt 0) { ($mx - $mn) / [double]$mx } else { 0.0 }
            # 基底 = 低饱和且不是纯黑阴影；高饱和（黄色标记）与深黑一律原样保留
            if ($sat -le 0.30 -and $mx -ge 120) {
                $l = 0.30 * $c.R + 0.59 * $c.G + 0.11 * $c.B
                $r = [Math]::Min(255.0, 0.5915 * $l + 33.6)
                $g = [Math]::Min(255.0, 0.6268 * $l + 34.9)
                $b = [Math]::Min(255.0, 0.6831 * $l + 37.2)
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A,
                        [int][Math]::Round($r), [int][Math]::Round($g), [int][Math]::Round($b)))
            } else {
                $out.SetPixel($x, $y, $c)
            }
        }
    }
    return $out
}

$topBmp = [System.Drawing.Bitmap]::FromFile($topSrc)
$sideBmp = [System.Drawing.Bitmap]::FromFile($sideSrc)
try {
    $topOut = Convert-TungstenBase $topBmp
    $topOut.Save((Join-Path $BlockDir 'spent_fuel_pool_top.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $topOut.Dispose()
    $sideOut = Convert-TungstenBase $sideBmp
    $sideOut.Save((Join-Path $BlockDir 'spent_fuel_pool.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $sideOut.Dispose()
} finally {
    $topBmp.Dispose()
    $sideBmp.Dispose()
}
Write-Host '  写出 spent_fuel_pool.png / spent_fuel_pool_top.png（HBM Spent Fuel Pool Drum，基底换钨色）'

# ---- 高放玻璃屏蔽块：深绿玻璃质建材（程序化，不拷贝外部素材）
$hlw = New-Object System.Drawing.Bitmap(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $edge = ($x -eq 0) -or ($x -eq 15) -or ($y -eq 0) -or ($y -eq 15)
        $v = (($x * 7 + $y * 11) % 9)
        if ($edge) {
            $r = 34 + $v; $g = 74 + $v * 2; $bl = 52 + $v
        } else {
            $r = 52 + $v * 2; $g = 112 + $v * 3; $bl = 78 + $v * 2
            if ((($x * 5 + $y * 3) % 19) -eq 0) { $r = 96; $g = 168; $bl = 118 }
            if ((($x + $y * 2) % 23) -eq 0) { $r = 28; $g = 62; $bl = 44 }
        }
        $hlw.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $bl))
    }
}
$hlw.Save((Join-Path $BlockDir 'hlw_glass_block.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$hlw.Dispose()
Write-Host '  写出 hlw_glass_block.png（深绿玻璃屏蔽块，程序化）'

# ---- 高放玻璃屏蔽块：blockstate / 模型 / 掉落表
$Assets = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution'
$Data   = Join-Path $ProjRoot 'src\main\resources\data\stellar_evolution'
New-Item -ItemType Directory -Force -Path (Join-Path $Assets 'blockstates'), (Join-Path $Assets 'models\block'), (Join-Path $Data 'loot_table\blocks') | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $Assets 'blockstates\hlw_glass_block.json'), @'
{
    "variants": {
        "": { "model": "stellar_evolution:block/hlw_glass_block" }
    }
}
'@, $utf8)
[System.IO.File]::WriteAllText((Join-Path $Assets 'models\block\hlw_glass_block.json'), @'
{
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "stellar_evolution:block/hlw_glass_block"
    }
}
'@, $utf8)
New-Item -ItemType Directory -Force -Path (Join-Path $Assets 'items') | Out-Null
[System.IO.File]::WriteAllText((Join-Path $Assets 'items\hlw_glass_block.json'), @'
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/hlw_glass_block"
    }
}
'@, $utf8)
[System.IO.File]::WriteAllText((Join-Path $Data 'loot_table\blocks\hlw_glass_block.json'), @'
{
  "type": "minecraft:block",
  "pools": [
    {
      "bonus_rolls": 0.0,
      "entries": [
        { "type": "minecraft:item", "name": "stellar_evolution:hlw_glass_block" }
      ],
      "rolls": 1.0
    }
  ],
  "random_sequence": "stellar_evolution:blocks/hlw_glass_block"
}
'@, $utf8)
Write-Host '  写出 hlw_glass_block 的 blockstate / 模型 / 掉落表'

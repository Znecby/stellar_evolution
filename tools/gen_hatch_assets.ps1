﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿# 仓室（hatch）资源生成：外部机壳基底换色 + 覆盖层原样拷贝 + 原版 GUI 改色
#
# 口径（2026-11-02）：
#   1. 基底 = 外部机壳 `machine_casing_sturdy_hsse_ctm.png` 左上角 16x16，按本项目「不锈钢」色系换色；
#   2. 覆盖层 = 外部 `block/overlay/machine/*` 原样拷贝（不改色）；
#   3. GUI = 原版 `generic_54.png` 的边框 / 槽位重新拼装，并做轻微冷灰调色（改原版素材）。
#
# 运行：powershell -ExecutionPolicy Bypass -File tools\gen_hatch_assets.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'src/main/resources/assets/stellar_evolution'
$blockDir = Join-Path $assets 'textures/block/hatch'
$guiDir = Join-Path $assets 'textures/gui/hatch'
New-Item -ItemType Directory -Force -Path $blockDir, $guiDir | Out-Null

# 外部机壳素材路径（仅再生成资源时使用）
$CasingSource = 'C:\MCmod项目\gregtech_fork_beyond_limits\src\main\resources\assets\gtceu\textures\block'

# ---------------------------------------------------------------- 1) 覆盖层原样拷贝
$overlays = @(
    'overlay/machine/overlay_pipe.png',
    'overlay/machine/overlay_item_hatch_input.png',
    'overlay/machine/overlay_item_hatch_output.png',
    'overlay/machine/overlay_fluid_hatch_input.png',
    'overlay/machine/overlay_fluid_hatch_output.png',
    'overlay/machine/overlay_dual_hatch_input.png',
    'overlay/machine/overlay_dual_hatch_output.png'
)
foreach ($rel in $overlays) {
    $from = Join-Path $CasingSource $rel
    $name = Split-Path $rel -Leaf
    Copy-Item -LiteralPath $from -Destination (Join-Path $blockDir $name) -Force
    Write-Host "overlay -> $name"
}

# ---------------------------------------------------------------- 2) 基变换色（不锈钢）
$ctm = [System.Drawing.Bitmap]::new((Join-Path $CasingSource 'casings/solid/machine_casing_sturdy_hsse_ctm.png'))
$base = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
# 暗 / 亮端点：冷灰不锈钢，介于深灰与近白之间
$dark = @(0x55, 0x5B, 0x60)
$light = @(0xE8, 0xEC, 0xEF)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $c = $ctm.GetPixel($x, $y)
        $lum = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
        $t = ($lum - 35.0) / 80.0
        if ($t -lt 0.0) { $t = 0.0 }
        if ($t -gt 1.0) { $t = 1.0 }
        $r = [int]($dark[0] + ($light[0] - $dark[0]) * $t)
        $g = [int]($dark[1] + ($light[1] - $dark[1]) * $t)
        $b = [int]($dark[2] + ($light[2] - $dark[2]) * $t)
        $base.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
    }
}
$base.Save((Join-Path $blockDir 'hatch_base.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$base.Dispose()

# 连接材质图集（32×32，GT CTM 布局）：同一套不锈钢色阶
$ctmOut = [System.Drawing.Bitmap]::new($ctm.Width, $ctm.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $ctm.Height; $y++) {
    for ($x = 0; $x -lt $ctm.Width; $x++) {
        $c = $ctm.GetPixel($x, $y)
        $lum = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
        $t = ($lum - 35.0) / 80.0
        if ($t -lt 0.0) { $t = 0.0 }
        if ($t -gt 1.0) { $t = 1.0 }
        $r = [int]($dark[0] + ($light[0] - $dark[0]) * $t)
        $g = [int]($dark[1] + ($light[1] - $dark[1]) * $t)
        $b = [int]($dark[2] + ($light[2] - $dark[2]) * $t)
        $ctmOut.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
    }
}
$ctmOut.Save((Join-Path $blockDir 'hatch_base_ctm.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$ctmOut.Dispose()
$ctm.Dispose()
Write-Host 'base -> hatch_base.png / hatch_base_ctm.png'

# ---------------------------------------------------------------- 3) GUI（改原版素材）
$tmp = Join-Path $env:TEMP 'se_hatch_gui'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$merged = Get-ChildItem (Join-Path $root 'build/moddev/artifacts') -Filter 'minecraft-patched-*-merged.jar' |
    Select-Object -First 1
if ($null -eq $merged) {
    throw '找不到 build/moddev/artifacts/minecraft-patched-*-merged.jar（先跑一次 compileJava / runClient）'
}
Push-Location $tmp
& jar xf $merged.FullName 'assets/minecraft/textures/gui/container/generic_54.png'
Pop-Location
$vanilla = [System.Drawing.Bitmap]::new((Join-Path $tmp 'assets/minecraft/textures/gui/container/generic_54.png'))

$W = 176
$H = 190
$SLOT_SRC = [System.Drawing.Rectangle]::new(7, 17, 18, 18)   # 通用槽位（含 1px 凹边）
$PANEL = [System.Drawing.Color]::FromArgb(198, 198, 198)

function Copy-Rect([System.Drawing.Bitmap]$dst, [System.Drawing.Bitmap]$src, [System.Drawing.Rectangle]$srcRect, [int]$dx, [int]$dy) {
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.DrawImage($src,
        [System.Drawing.Rectangle]::new($dx, $dy, $srcRect.Width, $srcRect.Height),
        $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
}

function New-HatchGui([string]$fileName, [bool]$withItems, [bool]$withTank, [int]$tankX) {
    $bmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear($PANEL)
    $g.Dispose()

    # 四边框（原版 generic_54 的边角 / 边条）
    Copy-Rect $bmp $vanilla ([System.Drawing.Rectangle]::new(0, 0, $W, 4)) 0 0
    Copy-Rect $bmp $vanilla ([System.Drawing.Rectangle]::new(0, 218, $W, 4)) 0 ($H - 4)
    Copy-Rect $bmp $vanilla ([System.Drawing.Rectangle]::new(0, 4, 4, $H - 8)) 0 4
    Copy-Rect $bmp $vanilla ([System.Drawing.Rectangle]::new(172, 4, 4, $H - 8)) 172 4

    # 机器槽位（4 × 4）
    if ($withItems) {
        for ($row = 0; $row -lt 4; $row++) {
            for ($col = 0; $col -lt 4; $col++) {
                Copy-Rect $bmp $vanilla $SLOT_SRC (8 + $col * 18 - 1) (18 + $row * 18 - 1)
            }
        }
    }
    # 流体槽：单个通用槽位（18 × 18）
    if ($withTank) {
        Copy-Rect $bmp $vanilla $SLOT_SRC ($tankX - 1) 17
    }
    # 玩家背包（3 行 + 快捷栏）
    for ($row = 0; $row -lt 3; $row++) {
        for ($col = 0; $col -lt 9; $col++) {
            Copy-Rect $bmp $vanilla $SLOT_SRC (8 + $col * 18 - 1) (108 + $row * 18 - 1)
        }
    }
    for ($col = 0; $col -lt 9; $col++) {
        Copy-Rect $bmp $vanilla $SLOT_SRC (8 + $col * 18 - 1) (166 - 1)
    }

    # 轻微冷灰调色 → 「本项目的不锈钢 GUI」
    for ($y = 0; $y -lt $H; $y++) {
        for ($x = 0; $x -lt $W; $x++) {
            $c = $bmp.GetPixel($x, $y)
            $r = [math]::Min(255, [int]($c.R * 0.97))
            $gg = [math]::Min(255, [int]($c.G * 0.99))
            $b = [math]::Min(255, [int]($c.B * 1.02))
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $gg, $b))
        }
    }
    $bmp.Save((Join-Path $guiDir $fileName), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "gui -> $fileName"
}

New-HatchGui 'item.png' $true $false 0
New-HatchGui 'fluid.png' $false $true 80
New-HatchGui 'dual.png' $true $true 98

$vanilla.Dispose()

# ---------------------------------------------------------------- 4) 方块状态 / 模型 / 物品定义
$blockStateDir = Join-Path $assets 'blockstates'
$modelDir = Join-Path $assets 'models/block/hatch'
$itemDir = Join-Path $assets 'items'
New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

$hatches = @(
    @{ id = 'item_input_hatch';   overlay = 'overlay_item_hatch_input' },
    @{ id = 'item_output_hatch';  overlay = 'overlay_item_hatch_output' },
    @{ id = 'fluid_input_hatch';  overlay = 'overlay_fluid_hatch_input' },
    @{ id = 'fluid_output_hatch'; overlay = 'overlay_fluid_hatch_output' },
    @{ id = 'dual_input_hatch';   overlay = 'overlay_dual_hatch_input' },
    @{ id = 'dual_output_hatch';  overlay = 'overlay_dual_hatch_output' }
)

$utf8 = [System.Text.UTF8Encoding]::new($false)
foreach ($h in $hatches) {
    $id = $h.id
    $modelId = "stellar_evolution:block/hatch/$id"

    $blockState = @"
{
    "variants": {
        "facing=north": { "model": "$modelId" },
        "facing=east": { "model": "$modelId", "y": 90 },
        "facing=south": { "model": "$modelId", "y": 180 },
        "facing=west": { "model": "$modelId", "y": 270 },
        "facing=up": { "model": "$modelId", "x": 270 },
        "facing=down": { "model": "$modelId", "x": 90 }
    }
}
"@
    [System.IO.File]::WriteAllText((Join-Path $blockStateDir "$id.json"), $blockState, $utf8)

    $model = @"
{
    "parent": "minecraft:block/block",
    "textures": {
        "base": "stellar_evolution:block/hatch/hatch_base",
        "pipe": "stellar_evolution:block/hatch/overlay_pipe",
        "overlay": "stellar_evolution:block/hatch/$($h.overlay)",
        "particle": "stellar_evolution:block/hatch/hatch_base"
    },
    "elements": [
        {
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": { "texture": "#base", "cullface": "down" },
                "up": { "texture": "#base", "cullface": "up" },
                "north": { "texture": "#base", "cullface": "north" },
                "south": { "texture": "#base", "cullface": "south" },
                "west": { "texture": "#base", "cullface": "west" },
                "east": { "texture": "#base", "cullface": "east" }
            }
        },
        {
            "from": [0, 0, -0.02],
            "to": [16, 16, 0],
            "shade": false,
            "faces": { "north": { "texture": "#pipe" } }
        },
        {
            "from": [0, 0, -0.04],
            "to": [16, 16, 0],
            "shade": false,
            "faces": { "north": { "texture": "#overlay" } }
        }
    ]
}
"@
    [System.IO.File]::WriteAllText((Join-Path $modelDir "$id.json"), $model, $utf8)

    $item = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "$modelId"
    }
}
"@
    [System.IO.File]::WriteAllText((Join-Path $itemDir "$id.json"), $item, $utf8)
    Write-Host "json -> $id"
}

# ---------------------------------------------------------------- 5) 掉落表（破坏后掉自己）
$lootDir = Join-Path $root 'src/main/resources/data/stellar_evolution/loot_table/blocks'
foreach ($h in $hatches) {
    $id = $h.id
    $loot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$id"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$id"
}
"@
    [System.IO.File]::WriteAllText((Join-Path $lootDir "$id.json"), $loot, $utf8)
    Write-Host "loot -> $id"
}

# ---------------------------------------------------------------- 6) 高负荷机械方块（仓室基底，连接材质）
$casingId = 'high_load_machine_casing'
$casingState = @"
{
    "variants": {
        "": {
            "type": "stellar_evolution:ctm_casing",
            "base": "stellar_evolution:block/hatch/hatch_base",
            "ctm": "stellar_evolution:block/hatch/hatch_base_ctm"
        }
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $blockStateDir "$casingId.json"), $casingState, $utf8)

$casingModel = @"
{
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "stellar_evolution:block/hatch/hatch_base"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $modelDir "$casingId.json"), $casingModel, $utf8)

$casingItem = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/hatch/$casingId"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $itemDir "$casingId.json"), $casingItem, $utf8)

$casingLoot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$casingId"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$casingId"
}
"@
[System.IO.File]::WriteAllText((Join-Path $lootDir "$casingId.json"), $casingLoot, $utf8)
Write-Host "json -> $casingId"

# ---------------------------------------------------------------- 7) 坚固支撑机械方块（钨色连接材质）
$supportSrcBase = Join-Path $CasingSource 'casings/gtnh_modern/botmium_casing.png'
$supportSrcCtm = Join-Path $CasingSource 'casings/gtnh_modern/botmium_casing_ctm.png'
$supportBase = Join-Path $blockDir 'rugged_support_machine_casing.png'
$supportCtm = Join-Path $blockDir 'rugged_support_machine_casing_ctm.png'

function Convert-ToTungsten([string]$srcPath, [string]$dstPath) {
    # 钨块调色：暗端 #3E4146（62,65,70）→ 亮端 #ACB4C2（172,180,194）
    $src = [System.Drawing.Bitmap]::new($srcPath)
    $out = [System.Drawing.Bitmap]::new($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $lum = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            $t = ($lum - 55.0) / 90.0
            if ($t -lt 0.0) { $t = 0.0 }
            if ($t -gt 1.0) { $t = 1.0 }
            $r = [int](62 + (172 - 62) * $t)
            $g = [int](65 + (180 - 65) * $t)
            $b = [int](70 + (194 - 70) * $t)
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
        }
    }
    $out.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    $src.Dispose()
}
Convert-ToTungsten $supportSrcBase $supportBase
Convert-ToTungsten $supportSrcCtm $supportCtm
Write-Host 'base -> rugged_support_machine_casing.png / _ctm.png'

$supportId = 'rugged_support_machine_casing'
$supportState = @"
{
    "variants": {
        "": {
            "type": "stellar_evolution:ctm_casing",
            "base": "stellar_evolution:block/hatch/$supportId",
            "ctm": "stellar_evolution:block/hatch/${supportId}_ctm"
        }
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $blockStateDir "$supportId.json"), $supportState, $utf8)

$supportModel = @"
{
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "stellar_evolution:block/hatch/$supportId"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $modelDir "$supportId.json"), $supportModel, $utf8)

$supportItem = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/hatch/$supportId"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $itemDir "$supportId.json"), $supportItem, $utf8)

$supportLoot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$supportId"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$supportId"
}
"@
[System.IO.File]::WriteAllText((Join-Path $lootDir "$supportId.json"), $supportLoot, $utf8)
Write-Host "json -> $supportId"

# ---------------------------------------------------------------- 8) 超导线圈方块（下界合金色 + 连接材质 + bloom）
$coilSrcDir = Join-Path $CasingSource 'casings/coils'
$coilId = 'superconducting_coil_block'
$coilBase = Join-Path $blockDir "$coilId.png"
$coilCtm = Join-Path $blockDir "${coilId}_ctm.png"
$coilBloom = Join-Path $blockDir "${coilId}_bloom.png"
$coilBloomCtm = Join-Path $blockDir "${coilId}_bloom_ctm.png"
$coilItem = Join-Path $blockDir "${coilId}_item.png"

function Convert-ToNetherite([string]$srcPath, [string]$dstPath) {
    # 下界合金块调色：按源图亮度范围线性映射到 #241E1F → #5A575A
    $src = [System.Drawing.Bitmap]::new($srcPath)
    $minL = 999.0; $maxL = -1.0
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $l = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            if ($l -lt $minL) { $minL = $l }
            if ($l -gt $maxL) { $maxL = $l }
        }
    }
    if ($maxL -le $minL) { $maxL = $minL + 1.0 }
    $out = [System.Drawing.Bitmap]::new($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $lum = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            $t = ($lum - $minL) / ($maxL - $minL)
            $r = [int](36 + (90 - 36) * $t)
            $g = [int](30 + (87 - 30) * $t)
            $b = [int](31 + (90 - 31) * $t)
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
        }
    }
    $out.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $out.Dispose()
    $src.Dispose()
}

Convert-ToNetherite (Join-Path $coilSrcDir 'machine_coil_electrumflux.png') $coilBase
Convert-ToNetherite (Join-Path $coilSrcDir 'machine_coil_electrumflux_ctm.png') $coilCtm
Convert-ToNetherite (Join-Path $coilSrcDir 'machine_coil_electrumflux_bloom.png') $coilBloom
Convert-ToNetherite (Join-Path $coilSrcDir 'machine_coil_electrumflux_bloom_ctm.png') $coilBloomCtm

# 动画帧信息（16×32 / 32×64 = 2 帧）
$coilMeta = @"
{
    "animation": {
        "frametime": 20,
        "interpolate": true,
        "frames": [
            0,
            { "index": 0, "time": 100 },
            1,
            { "index": 1, "time": 20 }
        ]
    }
}
"@
foreach ($p in @($coilBase, $coilCtm, $coilBloom, $coilBloomCtm)) {
    [System.IO.File]::WriteAllText("$p.mcmeta", $coilMeta, $utf8)
}

# 物品栏用静态首帧（16×16）
$baseImage = [System.Drawing.Bitmap]::new($coilBase)
$itemImage = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $itemImage.SetPixel($x, $y, $baseImage.GetPixel($x, $y))
    }
}
$itemImage.Save($coilItem, [System.Drawing.Imaging.ImageFormat]::Png)
$itemImage.Dispose()
$baseImage.Dispose()
Write-Host 'base -> superconducting_coil_block.png / _ctm / _bloom / _bloom_ctm / _item'

$coilState = @"
{
    "variants": {
        "": {
            "type": "stellar_evolution:ctm_casing",
            "base": "stellar_evolution:block/hatch/$coilId",
            "ctm": "stellar_evolution:block/hatch/${coilId}_ctm",
            "bloom": "stellar_evolution:block/hatch/${coilId}_bloom",
            "bloom_ctm": "stellar_evolution:block/hatch/${coilId}_bloom_ctm"
        }
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $blockStateDir "$coilId.json"), $coilState, $utf8)

$coilModel = @"
{
    "parent": "minecraft:block/cube_all",
    "textures": {
        "all": "stellar_evolution:block/hatch/${coilId}_item"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $modelDir "$coilId.json"), $coilModel, $utf8)

$coilItemJson = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/hatch/$coilId"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $itemDir "$coilId.json"), $coilItemJson, $utf8)

$coilLoot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$coilId"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$coilId"
}
"@
[System.IO.File]::WriteAllText((Join-Path $lootDir "$coilId.json"), $coilLoot, $utf8)
Write-Host "json -> $coilId"

# ---------------------------------------------------------------- 9) 机械物流仓（基底 + 8×8 铜块覆盖层）
Push-Location $tmp
& jar xf $merged.FullName 'assets/minecraft/textures/block/copper_block.png'
Pop-Location
$copper = [System.Drawing.Bitmap]::new((Join-Path $tmp 'assets/minecraft/textures/block/copper_block.png'))
$logisticsOverlay = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($logisticsOverlay)
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$graphics.DrawImage($copper, [System.Drawing.Rectangle]::new(4, 4, 8, 8), 0, 0, 16, 16, [System.Drawing.GraphicsUnit]::Pixel)
$graphics.Dispose()
$logisticsOverlay.Save((Join-Path $blockDir 'overlay_machine_logistics.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$logisticsOverlay.Dispose()
$copper.Dispose()
Write-Host 'overlay -> overlay_machine_logistics.png'

$logisticsId = 'machine_logistics_hatch'
$logisticsState = @"
{
    "variants": {
        "facing=north": { "model": "stellar_evolution:block/hatch/$logisticsId" },
        "facing=east": { "model": "stellar_evolution:block/hatch/$logisticsId", "y": 90 },
        "facing=south": { "model": "stellar_evolution:block/hatch/$logisticsId", "y": 180 },
        "facing=west": { "model": "stellar_evolution:block/hatch/$logisticsId", "y": 270 },
        "facing=up": { "model": "stellar_evolution:block/hatch/$logisticsId", "x": 270 },
        "facing=down": { "model": "stellar_evolution:block/hatch/$logisticsId", "x": 90 }
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $blockStateDir "$logisticsId.json"), $logisticsState, $utf8)

$logisticsModel = @"
{
    "parent": "minecraft:block/block",
    "textures": {
        "base": "stellar_evolution:block/hatch/hatch_base",
        "overlay": "stellar_evolution:block/hatch/overlay_machine_logistics",
        "particle": "stellar_evolution:block/hatch/hatch_base"
    },
    "elements": [
        {
            "from": [0, 0, 0],
            "to": [16, 16, 16],
            "faces": {
                "down": { "texture": "#base", "cullface": "down" },
                "up": { "texture": "#base", "cullface": "up" },
                "north": { "texture": "#base", "cullface": "north" },
                "south": { "texture": "#base", "cullface": "south" },
                "west": { "texture": "#base", "cullface": "west" },
                "east": { "texture": "#base", "cullface": "east" }
            }
        },
        {
            "from": [0, 0, -0.02],
            "to": [16, 16, 0],
            "shade": false,
            "faces": { "north": { "texture": "#overlay" } }
        }
    ]
}
"@
[System.IO.File]::WriteAllText((Join-Path $modelDir "$logisticsId.json"), $logisticsModel, $utf8)

$logisticsItem = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/hatch/$logisticsId"
    }
}
"@
[System.IO.File]::WriteAllText((Join-Path $itemDir "$logisticsId.json"), $logisticsItem, $utf8)

$logisticsLoot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$logisticsId"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$logisticsId"
}
"@
[System.IO.File]::WriteAllText((Join-Path $lootDir "$logisticsId.json"), $logisticsLoot, $utf8)
Write-Host "json -> $logisticsId"

Write-Host 'hatch assets done.'

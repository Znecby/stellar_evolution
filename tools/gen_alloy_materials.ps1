﻿﻿﻿# gen_alloy_materials.ps1 —— 合金材料全套贴图 / 模型 / 数据（2026-11-01，第二十五批）。
#
# 口径（与钨 / 铅材料一致）：
#   · 7 种合金 × 8 形态（粉 / 锭 / 粒 / 板 / 杆 / 线 / 齿轮 / 块）：
#       7 个扁平物品用 `模板/材料/浅色/material_<shape>.png` 灰度模板按合金基色上色；
#       块用同一套模板的 block 形态；
#   · 8 种配料元素粉（铬/锰/镍/铌/钛/铝/钼/锆）+ 钢粉：dust 模板上色；
#   · 每个合金块生成 blockstate / models/block / items 定义 / 掉落表 / 9 锭存储配方；
#   · 每个合金生成 锭→粒 / 粒→锭 / 锭→板 / 锭→杆 / 锭→线 / 锭+板→齿轮 的数据包配方。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_alloy_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot   = Split-Path -Parent $PSScriptRoot
$AssetRoot  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution'
$DataRoot   = Join-Path $ProjRoot 'src\main\resources\data\stellar_evolution'
$ItemDir    = Join-Path $AssetRoot 'textures\item'
$BlockDir   = Join-Path $AssetRoot 'textures\block'
$ModelsDir  = Join-Path $AssetRoot 'models\item'
$BlockModels= Join-Path $AssetRoot 'models\block'
$Blockstates= Join-Path $AssetRoot 'blockstates'
$ItemsDir   = Join-Path $AssetRoot 'items'
$LootDir    = Join-Path $DataRoot 'loot_table\blocks'
$RecipeDir  = Join-Path $DataRoot 'recipe'
$TplLight   = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $BlockDir, $ModelsDir, $BlockModels, $Blockstates, $ItemsDir, $LootDir, $RecipeDir | Out-Null

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

# ---- 合金表：id / 基色（与 AlloyMaterials.java 保持一致）
$Alloys = @(
    @{ id = 'high_speed_steel';                    rgb = @(124, 134, 152) },
    @{ id = 'stainless_steel';                     rgb = @(200, 206, 214) },
    @{ id = 'tungsten_copper_hard_alloy';          rgb = @(126, 112,  98) },
    @{ id = 'nickel_superalloy';                   rgb = @(172, 174, 162) },
    @{ id = 'titanium_alloy';                      rgb = @(150, 164, 180) },
    @{ id = 'aluminum_magnesium_alloy';            rgb = @(196, 200, 206) }
)

# ---- 1) 合金物品：锭 / 粒 / 板 / 杆 / 线 / 齿轮 + 方块
$Shapes = @('ingot', 'nugget', 'plate', 'rod', 'wire', 'gear')
foreach ($alloy in $Alloys) {
    $r = $alloy.rgb[0]; $g = $alloy.rgb[1]; $b = $alloy.rgb[2]
    foreach ($shape in $Shapes) {
        $id = "{0}_{1}" -f $alloy.id, $shape
        Save (New-TintedItem $shape $r $g $b) (Join-Path $ItemDir ("{0}.png" -f $id))
        Write-FlatItemModel $id
    }
    $blockId = "{0}_block" -f $alloy.id
    Save (New-TintedItem 'block' $r $g $b) (Join-Path $BlockDir ("{0}.png" -f $blockId))
    Write-BlockModel $blockId
    Write-Host ("  {0}: 7 shapes done" -f $alloy.id)
}

# ---- 2) 配料元素粉 + 钢粉
$Dusts = @(
    @{ id = 'chromium_dust';    rgb = @(198, 204, 214) },
    @{ id = 'nickel_dust';      rgb = @(198, 201, 184) },
    @{ id = 'niobium_dust';     rgb = @(176, 174, 160) },
    @{ id = 'titanium_dust';    rgb = @(184, 194, 200) },
    @{ id = 'aluminum_dust';    rgb = @(214, 220, 224) },
    @{ id = 'molybdenum_dust';  rgb = @(159, 166, 174) },
    @{ id = 'zirconium_dust';   rgb = @(196, 203, 192) },
    @{ id = 'steel_dust';       rgb = @(140, 144, 150) }
)
foreach ($dust in $Dusts) {
    Save (New-TintedItem 'dust' $dust.rgb[0] $dust.rgb[1] $dust.rgb[2]) `
        (Join-Path $ItemDir ("{0}.png" -f $dust.id))
    Write-FlatItemModel $dust.id
}
Write-Host ("  element / steel dust: {0} done" -f $Dusts.Count)

# ---- 3) 方块掉落表
foreach ($alloy in $Alloys) {
    $blockId = "{0}_block" -f $alloy.id
    $loot = @"
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:$blockId"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/$blockId"
}
"@
    Set-Content -Path (Join-Path $LootDir ("{0}.json" -f $blockId)) -Value $loot -Encoding UTF8
}

# ---- 4) 数据包配方：块 / 粒 / 板 / 杆 / 线 / 齿轮
foreach ($alloy in $Alloys) {
    $id = $alloy.id
    $blockId = "${id}_block"

    $blockRecipe = @"
{
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "I": "stellar_evolution:${id}_ingot"
    },
    "pattern": [
        "III",
        "III",
        "III"
    ],
    "result": {
        "id": "stellar_evolution:$blockId"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}.json" -f $blockId)) -Value $blockRecipe -Encoding UTF8

    $ingotFromBlock = @"
{
    "type": "minecraft:crafting_shapeless",
    "category": "misc",
    "ingredients": [
        "stellar_evolution:$blockId"
    ],
    "result": {
        "count": 9,
        "id": "stellar_evolution:${id}_ingot"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_ingot_from_block.json" -f $id)) -Value $ingotFromBlock -Encoding UTF8

    $nugget = @"
{
    "type": "minecraft:crafting_shapeless",
    "category": "misc",
    "ingredients": [
        "stellar_evolution:${id}_ingot"
    ],
    "result": {
        "count": 9,
        "id": "stellar_evolution:${id}_nugget"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_nugget.json" -f $id)) -Value $nugget -Encoding UTF8

    $ingotFromNuggets = @"
{
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "N": "stellar_evolution:${id}_nugget"
    },
    "pattern": [
        "NNN",
        "NNN",
        "NNN"
    ],
    "result": {
        "id": "stellar_evolution:${id}_ingot"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_ingot_from_nuggets.json" -f $id)) -Value $ingotFromNuggets -Encoding UTF8

    $plate = @"
{
    "type": "minecraft:stonecutting",
    "ingredient": "stellar_evolution:${id}_ingot",
    "result": {
        "id": "stellar_evolution:${id}_plate"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_plate_from_{0}_ingot_stonecutting.json" -f $id)) -Value $plate -Encoding UTF8

    $rod = @"
{
    "type": "minecraft:stonecutting",
    "ingredient": "stellar_evolution:${id}_ingot",
    "result": {
        "count": 2,
        "id": "stellar_evolution:${id}_rod"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_rod_from_{0}_ingot_stonecutting.json" -f $id)) -Value $rod -Encoding UTF8

    $wire = @"
{
    "type": "minecraft:stonecutting",
    "ingredient": "stellar_evolution:${id}_ingot",
    "result": {
        "count": 8,
        "id": "stellar_evolution:${id}_wire"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_wire_from_{0}_ingot_stonecutting.json" -f $id)) -Value $wire -Encoding UTF8

    $gear = @"
{
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "key": {
        "I": "stellar_evolution:${id}_ingot",
        "P": "stellar_evolution:${id}_plate"
    },
    "pattern": [
        " P ",
        "PIP",
        " P "
    ],
    "result": {
        "count": 2,
        "id": "stellar_evolution:${id}_gear"
    }
}
"@
    Set-Content -Path (Join-Path $RecipeDir ("{0}_gear.json" -f $id)) -Value $gear -Encoding UTF8
}

Write-Host ("Done: {0} alloys x 7 shapes + {1} dusts + blocks / loot / recipes." -f $Alloys.Count, $Dusts.Count)

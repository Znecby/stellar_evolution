﻿# gen_fission_materials.ps1 —— 生成核裂变线（F0）的物品图标 / 流体贴图 / 桶图标。
#
# 口径（2026-10-15 改版，与石化线 / 生化线统一）：
#   · **物品 = 用 `模板/材料/浅色` 的灰度材料模板上色**（本线 12 件形状全部对得上）：
#       锭 ingot  → 富集铀 / 贫铀 / 钚 / U-233
#       杆 rod    → 燃料棒 / MOX 棒 / 钍棒 / 乏燃料
#       粉 dust   → 铀精矿（黄饼）/ 高放废料
#       粒 nugget → 辐照同位素 / 稳定核素
#     上色 = 按模板自身亮度范围归一化后乘系数（k = 0.55 + 0.90×t）→ 保留模板的立体高光与阴影。
#   · 流体：统一取原版水动画条换色（tools/lib_water_fluid.ps1）→ block/<name>_still.png（16×512）+ block/<name>_flow.png（32×1024），各带 .mcmeta；
#   · 桶：以原版水桶贴图（tools/templates/water_bucket.png）为模板重着色；
#   · 物品模型：每件产 models/item/<name>.json + items/<name>.json（1.21.4+ 物品模型定义体系，要两份）。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_fission_materials.ps1
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
    Write-Host ("  写出 {0}" -f (Split-Path -Leaf $path))
}
function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

# ==================================================================== 1) 材料模板上色（平面图标）

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

# 聚变芯核：3D 小方块图标，六个面统一取沉重核心贴图的「顶面」区域（0,0,8,8）。
function Write-FusionCoreModel([string]$name) {
    $model = @"
{
    "display": {
        "gui": { "rotation": [30, 225, 0], "translation": [0, 3, 0], "scale": [1, 1, 1] },
        "ground": { "rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] },
        "fixed": { "rotation": [0, 180, 0], "translation": [0, 4, 0], "scale": [1, 1, 1] },
        "thirdperson_righthand": { "rotation": [45, 45, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5] }
    },
    "texture_size": [16, 16],
    "textures": {
        "all": "stellar_evolution:item/$name",
        "particle": "stellar_evolution:item/$name"
    },
    "elements": [
        {
            "name": "fusion_core",
            "from": [4, 0, 4],
            "to": [12, 8, 12],
            "faces": {
                "north": { "uv": [0, 0, 8, 8], "texture": "all" },
                "east":  { "uv": [0, 0, 8, 8], "texture": "all" },
                "south": { "uv": [0, 0, 8, 8], "texture": "all" },
                "west":  { "uv": [0, 0, 8, 8], "texture": "all" },
                "up":    { "uv": [0, 0, 8, 8], "texture": "all" },
                "down":  { "uv": [0, 0, 8, 8], "texture": "all" }
            }
        }
    ]
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

# 原版材质重着色：按亮度归一化后乘基色；用于「沉重核心」这类自带明暗层次的图标。
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

# 物品 id -> 材料模板形状 + 基色（RGB）
$items = @(
    @{ id = 'yellowcake';         shape = 'dust';   rgb = @(226, 206,  96) },  # 铀精矿（黄饼）：黄色粉末
    @{ id = 'u235';               shape = 'nugget'; rgb = @( 96, 150, 214) },  # 铀-235 粒：蓝灰
    @{ id = 'u238';               shape = 'nugget'; rgb = @( 90,  96, 104) },  # 铀-238 粒：深灰
    @{ id = 'plutonium';          shape = 'ingot';  rgb = @(200, 176, 150) },  # 钚：银白金属锭
    @{ id = 'u233';               shape = 'ingot';  rgb = @(176, 196, 176) },  # U-233：淡绿银锭
    @{ id = 'mox_fuel_rod';       shape = 'rod';    rgb = @(150, 120, 200) },  # MOX 棒（低）：紫灰
    @{ id = 'mox_fuel_rod_mid';   shape = 'rod';    rgb = @(138, 102, 194) },  # MOX 棒（中）
    @{ id = 'mox_fuel_rod_high';  shape = 'rod';    rgb = @(126,  84, 188) },  # MOX 棒（高）
    @{ id = 'thorium_fuel_rod';   shape = 'rod';    rgb = @(190, 176, 120) },  # 钍棒（低）：黄褐
    @{ id = 'thorium_fuel_rod_mid';  shape = 'rod'; rgb = @(202, 184, 112) },  # 钍棒（中）
    @{ id = 'thorium_fuel_rod_high'; shape = 'rod'; rgb = @(214, 192, 104) },  # 钍棒（高）
    @{ id = 'u233_seed_rod';          shape = 'rod'; rgb = @(150, 200, 170) }, # U-233 钍种子棒：淡绿银
    @{ id = 'ma_transmutation_rod';   shape = 'rod'; rgb = @(150, 170, 110) }, # MA 嬗变棒：橄榄绿
    @{ id = 'pu_inert_matrix_rod';    shape = 'rod'; rgb = @(150, 140, 170) }, # Pu 惰性基体棒：灰紫
    @{ id = 'burnable_poison_rod';    shape = 'rod'; rgb = @( 84,  96, 110) }, # 可燃毒物棒：蓝灰
    @{ id = 'uc_high_power_rod';      shape = 'rod'; rgb = @(210, 200, 160) }, # UC 高功率棒：暖白
    @{ id = 'uzr_metal_rod';          shape = 'rod'; rgb = @(190, 196, 176) }, # U-Zr 金属棒：青灰
    @{ id = 'spent_u233_seed';        shape = 'rod'; rgb = @( 92, 106,  88) }, # 乏燃料（U-233 种子）
    @{ id = 'spent_ma_rod';           shape = 'rod'; rgb = @(104,  98,  76) }, # 乏燃料（MA 嬗变）
    @{ id = 'spent_pu_inert';         shape = 'rod'; rgb = @( 96,  88,  98) }, # 乏燃料（Pu 惰性基体）
    @{ id = 'spent_poison_rod';       shape = 'rod'; rgb = @( 80,  84,  86) }, # 乏燃料（可燃毒物）
    @{ id = 'spent_uc_rod';           shape = 'rod'; rgb = @( 96,  92,  78) }, # 乏燃料（UC）
    @{ id = 'spent_uzr_rod';          shape = 'rod'; rgb = @( 98,  94,  86) }, # 乏燃料（U-Zr）
    @{ id = 'star_core_pellet';       shape = 'nugget'; rgb = @(220, 180, 255) }, # 星核粒：淡紫
    @{ id = 'star_ash';               shape = 'dust';   rgb = @( 96,  78, 120) }, # 星骸粉：暗紫
    @{ id = 'star_core_slag';         shape = 'dust';   rgb = @(120,  96, 140) }, # 星核渣：灰紫
    @{ id = 'raw_ember_essence';      shape = 'nugget'; rgb = @(200, 110,  60) }, # 粗离火素：暗橙
    @{ id = 'ember_essence';          shape = 'nugget'; rgb = @(255, 140,  60) }, # 离火素：橙
    @{ id = 'silent_ash';             shape = 'dust';   rgb = @(110, 110, 104) }, # 寂灰：灰
    @{ id = 'ember_fuel_rod_shell';   shape = 'rod';    rgb = @(120,  70,  40) }, # 离火燃料棒外壳：暗铜
    @{ id = 'unquenchable_alloy';     shape = 'ingot';  rgb = @(140,  60,  50) }, # 不熄合金锭：暗红
    @{ id = 'unstable_ember_fuel_rod'; shape = 'rod';   rgb = @(255, 120,  50) }, # 不稳定离火燃料棒：亮橙
    @{ id = 'stable_ember_fuel_rod';  shape = 'rod';    rgb = @(220, 150,  60) }, # 稳定离火燃料棒：琥珀
    @{ id = 'spent_ember_fuel_rod';   shape = 'rod';    rgb = @(100,  92,  84) }, # 离火棒乏燃料：灰烬
    @{ id = 'thorium_powder';     shape = 'dust';   rgb = @(196, 182, 126) },  # 钍粉：黄褐粉末
    @{ id = 'spent_fuel';         shape = 'rod';    rgb = @( 96,  84,  70) },  # 乏燃料（低浓铀）：焦褐
    @{ id = 'spent_fuel_u_mid';   shape = 'rod';    rgb = @(104,  88,  70) },  # 乏燃料（中浓铀）
    @{ id = 'spent_fuel_u_high';  shape = 'rod';    rgb = @(112,  92,  72) },  # 乏燃料（高浓铀）
    @{ id = 'spent_fuel_mox_low'; shape = 'rod';    rgb = @( 92,  74,  98) },  # 乏燃料（MOX 低）
    @{ id = 'spent_fuel_mox_mid'; shape = 'rod';    rgb = @(100,  78, 104) },  # 乏燃料（MOX 中）
    @{ id = 'spent_fuel_mox_high';shape = 'rod';    rgb = @(108,  82, 110) },  # 乏燃料（MOX 高）
    @{ id = 'spent_fuel_th_low';  shape = 'rod';    rgb = @(110, 104,  72) },  # 辐照钍（低）
    @{ id = 'spent_fuel_th_mid';  shape = 'rod';    rgb = @(118, 110,  76) },  # 辐照钍（中）
    @{ id = 'spent_fuel_th_high'; shape = 'rod';    rgb = @(126, 116,  82) },  # 辐照钍（高）
    @{ id = 'nuclear_waste';      shape = 'dust';   rgb = @( 96, 110,  80) },  # 高放废料：墨绿渣
    @{ id = 'short_lived_waste';  shape = 'dust';   rgb = @(214, 132,  58) },  # 短衰变废料：橙热渣
    @{ id = 'long_lived_waste';   shape = 'dust';   rgb = @( 70, 112, 112) },  # 长衰变废料：暗青渣
    @{ id = 'strontium_90';       shape = 'nugget'; rgb = @(214, 116, 116) },  # 锶-90：红粉
    @{ id = 'cesium_137';         shape = 'nugget'; rgb = @(218, 198, 112) },  # 铯-137：金黄
    @{ id = 'technetium_99';      shape = 'nugget'; rgb = @(154, 178, 190) },  # 锝-99：银蓝
    @{ id = 'technetium_steel';   shape = 'ingot';  rgb = @(166, 186, 198) },  # 锝钢锭：锝-99 + 钢的银蓝合金
    @{ id = 'hardened_alloy';     shape = 'ingot';  rgb = @(150, 158, 172) },  # 辐照硬化合金：铅离子注入的灰钢
    @{ id = 'platinum_group';     shape = 'nugget'; rgb = @(204, 204, 214) },  # 铂族：银白
    @{ id = 'minor_actinides';    shape = 'dust';   rgb = @(116, 132, 92) },   # 次锕系：橄榄渣
    @{ id = 'superheavy_precursor'; shape = 'nugget'; rgb = @(168, 120, 200) }, # 超重元素前体：灰紫（回旋产物）
    @{ id = 'superheavy_element'; shape = 'nugget'; rgb = @(196, 108, 214) },  # 鈇-298：紫（对撞机产物）
    @{ id = 'livermorium_300'; shape = 'nugget'; rgb = @(150, 92, 196) },      # 鉝-300：深紫
    @{ id = 'copernicium_304'; shape = 'nugget'; rgb = @(120, 70, 170) },      # 鎶-304：暗紫
    @{ id = 'exotic_matter';      shape = 'nugget'; rgb = @(220, 126, 244) },  # 奇异物质：亮紫
    @{ id = 'antimatter';         shape = 'nugget'; rgb = @(238, 238, 255) },  # 反物质：冷白辉光
    @{ id = 'leach_residue';      shape = 'dust';   rgb = @(110, 104,  92) },  # 浸出渣：灰褐
    @{ id = 'radioactive_sludge'; shape = 'dust';   rgb = @( 88, 132,  96) },  # 放射性污泥：暗绿粉
    @{ id = 'adu_cake';           shape = 'dust';   rgb = @(222, 210, 150) },  # 重铀酸铵滤饼：浅黄
    @{ id = 'uo2_powder';         shape = 'dust';   rgb = @( 78,  92,  74) },  # 二氧化铀粉：墨绿
    @{ id = 'lithium_dust';       shape = 'dust';   rgb = @(214, 218, 224) },  # 锂粉：银白
    @{ id = 'helium_3';           shape = 'nugget'; rgb = @(160, 222, 240) },  # 氦-3：浅青
    @{ id = 'lithium_6';          shape = 'dust';   rgb = @(206, 224, 236) },  # 锂-6：冷白
    @{ id = 'boron_11';           shape = 'nugget'; rgb = @(126, 112,  96) },  # 硼-11：灰褐
    @{ id = 'cobalt_dust';        shape = 'dust';   rgb = @( 92, 128, 186) },  # 钴粉：钴蓝
    @{ id = 'co_60';              shape = 'nugget'; rgb = @( 96, 196, 236) },  # 钴-60：亮青蓝
    @{ id = 'zr_90';              shape = 'nugget'; rgb = @(150, 170, 190) },  # 锆-90：银灰
    @{ id = 'mo_95';              shape = 'nugget'; rgb = @(120, 140, 160) },  # 钼-95：蓝灰
    @{ id = 'nd_143';             shape = 'nugget'; rgb = @(190, 150, 210) },  # 钕-143：淡紫
    @{ id = 'neptunium_237';      shape = 'nugget'; rgb = @(150, 168, 128) },  # 镎-237：橄榄绿
    @{ id = 'plutonium_238';      shape = 'nugget'; rgb = @(214, 176, 120) },  # 钚-238：暖橙
    @{ id = 'americium_241';      shape = 'nugget'; rgb = @(204, 148, 96) },   # 镅-241：橙棕
    @{ id = 'curium_244';         shape = 'nugget'; rgb = @(226, 140, 150) },  # 锔-244：玫红
    @{ id = 'iodine_129';         shape = 'nugget'; rgb = @(118,  74, 148) },  # 碘-129：碘紫
    @{ id = 'samarium_149';       shape = 'nugget'; rgb = @(222, 182, 202) },  # 钐-149：淡玫
    @{ id = 'hlw_glass';          shape = 'nugget'; rgb = @( 74, 118,  86) },  # 高放玻璃固化体：深绿
    @{ id = 'fuel_rod_leu';       shape = 'rod';    rgb = @(150, 182, 196) },  # 低浓缩铀燃料棒：银蓝
    @{ id = 'fuel_rod_meu';       shape = 'rod';    rgb = @(206, 176, 102) },  # 中浓缩铀燃料棒：铜金
    @{ id = 'fuel_rod_heu';       shape = 'rod';    rgb = @(214, 226, 142) },  # 高浓缩铀燃料棒：亮绿
    @{ id = 'zircaloy_tube';      shape = 'rod';    rgb = @(182, 200, 206) },  # 锆合金管：银灰
    @{ id = 'boron_carbide_rod';  shape = 'rod';    rgb = @( 72,  76,  84) },  # 碳化硼控制棒：深灰
    @{ id = 'graphite_rod';       shape = 'rod';    rgb = @( 58,  60,  64) },  # 石墨棒：炭黑
    @{ id = 'spent_graphite_rod'; shape = 'rod';    rgb = @( 96,  92,  84) },  # 废石墨棒：灰褐
    @{ id = 'reflector_rod';      shape = 'rod';    rgb = @(188, 196, 204) },  # 反射棒：银亮
    @{ id = 'superheavy_reflector_rod'; shape = 'rod'; rgb = @(198, 150, 232) },  # 超重反射棒：紫亮
    @{ id = 'exotic_reflector_rod'; shape = 'rod'; rgb = @(230, 160, 255) },      # 奇异反射棒：亮紫
    @{ id = 'radioactive_slag';   shape = 'dust';   rgb = @( 76, 138,  92) }   # 放射性熔渣：暗绿渣
)

foreach ($it in $items) {
    $bmp = New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]
    Save $bmp (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json（models/item + items，模板={1}）" -f $it.id, $it.shape)
}

# 聚变芯核（氘氚）：原版「沉重核心」灰度贴图 + 青白重着色（2026-10-26 改）
$fusionCore = New-RecoloredVanilla 'assets/minecraft/textures/block/heavy_core.png' 148 220 226
Save $fusionCore (Join-Path $ItemDir 'dt_fusion_core.png')
Write-FusionCoreModel 'dt_fusion_core'
Write-Host '  dt_fusion_core.json（3D 小方块，六面=顶面 0,0,8,8，模板=heavy_core）'

# 二期 A1：三种新芯核（同一模板，不同重着色）
$coreVariants = @(
    @{ id = 'dd_fusion_core';   rgb = @(202, 228, 246) },  # 氘氘：冷蓝
    @{ id = 'dhe3_fusion_core'; rgb = @(160, 236, 202) },  # 氘-氦3：青绿
    @{ id = 'pb11_fusion_core'; rgb = @(240, 190, 122) },  # 质子-硼11：琥珀
    @{ id = 'star_fusion_core'; rgb = @(255, 190, 120) }   # 星核：橙白（§7.12）
)
foreach ($v in $coreVariants) {
    $bmp = New-RecoloredVanilla 'assets/minecraft/textures/block/heavy_core.png' $v.rgb[0] $v.rgb[1] $v.rgb[2]
    Save $bmp (Join-Path $ItemDir ("{0}.png" -f $v.id))
    Write-FusionCoreModel $v.id
    Write-Host ("  {0}.json（3D 小方块，模板=heavy_core）" -f $v.id)
}

# ==================================================================== 2) 桶图标（重着色模板）

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

# ==================================================================== 3) 流体贴图 + 桶

$fluids = @(
    @{ name = 'uranium_hexafluoride'; rgb = @(180, 190, 170) }, # 六氟化铀：灰绿
    @{ name = 'nitric_acid';          rgb = @(228, 226, 150) }, # 硝酸：淡黄
    @{ name = 'loaded_extractant';    rgb = @(196, 160,  70) }, # 载铀有机相：琥珀
    @{ name = 'raffinate';            rgb = @(176, 186, 160) }, # 萃余液：灰绿
    @{ name = 'ammonia_solution';     rgb = @(190, 220, 225) }, # 氨水：浅青
    @{ name = 'hydrogen_fluoride';    rgb = @(200, 214, 190) }, # 氟化氢：浅灰绿
    @{ name = 'fluorine';             rgb = @(210, 232, 120) }, # 氟气：黄绿
    @{ name = 'sodium';               rgb = @(200, 200, 205) }, # 液态钠：银白
    @{ name = 'molten_lead';          rgb = @(92, 94, 98) },    # 液态铅：深灰
    @{ name = 'helium_coolant';       rgb = @(228, 240, 250) }, # 氦：极浅蓝
    @{ name = 'steam';                rgb = @(214, 220, 226) }, # 蒸汽：浅灰
    @{ name = 'heavy_water';          rgb = @(148, 188, 206) }, # 重水：浅蓝
    @{ name = 'deuterium';            rgb = @(186, 214, 228) }, # 氘：极浅蓝
    @{ name = 'tritium';              rgb = @(182, 240, 214) }, # 氚：浅青绿
    @{ name = 'dt_fuel';              rgb = @(168, 228, 220) }  # 氘氚燃料：青绿
)

foreach ($f in $fluids) {
    $r = [int]$f.rgb[0]; $g = [int]$f.rgb[1]; $bl = [int]$f.rgb[2]

    # 原版水动画条换色（32 帧 still + 32 帧 flow，各带 mcmeta）
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

Write-Host ("完成：物品图标 {0} 张（材料模板上色）、流体贴图 {1} 张、桶图标 {2} 张。" -f `
    $items.Count, ($fluids.Count * 2), $fluids.Count)

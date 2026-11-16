﻿# gen_petro_materials.ps1 —— 生成石化线的物品图标 / 流体贴图 / 桶图标 / 油砂岩方块贴图。
#
# 口径（2026-10-15 改版）：
#   · **物品 = 两条路子**（照项目既有素材口径）：
#       ① 形状对得上的 → 用 `模板/材料/浅色` 的**灰度材料模板**（锭 ingot / 粒 nugget / 板 plate /
#          杆 rod / 齿轮 gear / 粉 dust）按目标基色上色 —— 保留模板自带的立体高光与阴影；
#          2026-11-08 起全部塑料 / 橡胶统一用 **ingot** 模板；
#       ② 形状做不出来的（只剩电路板基材）→ 走 **3D 立方体模型**
#          （parent = minecraft:block/block），各面直接复用**原版方块贴图**，不新增贴图素材。
#   · 方块：block/oil_shale.png（深板岩系灰底 + 油斑）；
#   · 流体：统一取原版水动画条换色（tools/lib_water_fluid.ps1）→ block/<name>_still.png（16×512）+ block/<name>_flow.png（32×1024），各带 .mcmeta；
#   · 桶：以原版水桶贴图（tools/templates/water_bucket.png）为模板重着色；
#   · 物品模型定义：平面件出 models/item/<id>.json（item/generated）+ items/<id>.json（要两份）。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_petro_materials.ps1
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
#
# 灰度模板 + 目标基色 → 按模板自身亮度范围归一化，再乘系数上色：
#   k = 0.55 + 0.90 * t   （t=0 阴影 → 0.55×基色；t=1 高光 → 1.45×基色）
# 这样模板原有的倒角 / 高光 / 阴影关系全部保留，只换色相。

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

# 平面件：物品 id -> 材料模板形状 + 基色（RGB）
$flatItems = @(
    @{ id = 'bakelite';         shape = 'ingot';  rgb = @( 96,  70,  52) },  # 电木：深棕热固树脂
    @{ id = 'pmma';             shape = 'ingot';  rgb = @(198, 226, 236) },  # 有机玻璃：透明淡蓝
    @{ id = 'ptfe';             shape = 'ingot';  rgb = @(238, 240, 238) },  # 聚四氟乙烯：白色蜡状
    @{ id = 'nylon';            shape = 'ingot';  rgb = @(232, 228, 214) },  # 尼龙：米白
    @{ id = 'sbr';              shape = 'ingot';  rgb = @(104,  96,  88) },  # 丁苯橡胶：淡棕
    @{ id = 'nbr';              shape = 'ingot';  rgb = @( 62,  76,  92) },  # 丁腈橡胶：蓝灰
    @{ id = 'cr';               shape = 'ingot';  rgb = @( 92, 104,  88) },  # 氯丁橡胶：灰绿
    @{ id = 'silicone_rubber';  shape = 'ingot';  rgb = @(156, 158, 166) },  # 硅橡胶：浅灰
    @{ id = 'petroleum_coke';   shape = 'dust';   rgb = @( 52,  50,  52) },  # 石油焦：近黑焦粒
    @{ id = 'sulfur_dust';      shape = 'dust';   rgb = @(226, 208,  80) },  # 硫粉：黄色粉末
    @{ id = 'crosslinked_plastic'; shape = 'ingot'; rgb = @( 86, 190, 196) }, # 交联塑料：青绿树脂
    @{ id = 'track_etch_membrane'; shape = 'ingot'; rgb = @(226, 214, 150) }, # 离子径迹膜：淡金
    @{ id = 'grafted_plastic';     shape = 'ingot'; rgb = @(128,  96, 168) }, # 放射性接枝塑料：紫树脂
    @{ id = 'chiral_polymer';      shape = 'ingot'; rgb = @(186, 150, 210) }  # 手性聚合物：淡紫树脂
)

foreach ($it in $flatItems) {
    $bmp = New-TintedItem $it.shape $it.rgb[0] $it.rgb[1] $it.rgb[2]
    Save $bmp (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json（models/item + items，模板={1}）" -f $it.id, $it.shape)
}

# ==================================================================== 2) 3D 物品模型（立方体拼装）
#
# 形状在 16×16×16 空间里用若干立方体拼；各面直接用**原版方块贴图**，零新增素材。
# parent = minecraft:block/block（自带物品栏 / 手持的显示变换）。

$FACES = 'down', 'up', 'north', 'south', 'west', 'east'

function New-Element($from, $to, $tex) {
    # PS 5.1 的 ConvertTo-Json 不能序列化嵌套哈希表 → 一律用 PSCustomObject 逐项 Add-Member
    $faceObj = New-Object System.Management.Automation.PSObject
    foreach ($f in $FACES) {
        $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value ([pscustomobject]@{ texture = "#$tex" })
    }
    return [pscustomobject]@{ from = $from; to = $to; faces = $faceObj }
}

$block3d = @{

    # 电路板基材：绿板 + 铜箔走线 + 金焊盘（**无元件**——元件是后面装上去的事）
    'circuit_substrate' = @{
        textures = @{ board = 'minecraft:block/green_concrete'; cu = 'minecraft:block/copper_block'; gold = 'minecraft:block/gold_block' }
        boxes = @(
            @{ from = @(2, 6, 2);      to = @(14, 8, 14);      tex = 'board' },
            @{ from = @(3, 8, 3);      to = @(6, 8.3, 4);      tex = 'cu' },
            @{ from = @(3, 8, 6);      to = @(4, 8.3, 12);     tex = 'cu' },
            @{ from = @(11, 8, 4);     to = @(13, 8.3, 12);    tex = 'cu' },
            @{ from = @(3, 8, 12);     to = @(4.5, 8.3, 13.5); tex = 'gold' },
            @{ from = @(11.5, 8, 12);  to = @(13, 8.3, 13.5);  tex = 'gold' }
        )
    }
}

foreach ($id in $block3d.Keys) {
    $spec = $block3d[$id]
    $elements = @()
    foreach ($b in $spec.boxes) { $elements += New-Element $b.from $b.to $b.tex }

    # 3D 物品模型必须补 particle（破坏粒子用），否则报 Missing texture references: particle
    $tex = @{}
    foreach ($k in $spec.textures.Keys) { $tex[$k] = $spec.textures[$k] }
    $tex['particle'] = $spec.textures[$spec.boxes[0].tex]

    $model = [pscustomobject]@{
        parent   = 'minecraft:block/block'
        textures = [pscustomobject]$tex
        elements = $elements
    }
    [System.IO.File]::WriteAllText((Join-Path $ModelsDir "$id.json"),
        ($model | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

    $item = [pscustomobject]@{ model = [pscustomobject]@{ type = 'minecraft:model'; model = "stellar_evolution:item/$id" } }
    [System.IO.File]::WriteAllText((Join-Path $ItemsDir "$id.json"),
        ($item | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))

    Write-Host ("  {0}  （3D，{1} 个立方体）" -f $id, $spec.boxes.Count)
}

# ==================================================================== 3) 油砂岩方块贴图

# 深板岩系灰底 + 细噪点 + 几处油斑（近黑）
$b = New-Bmp 16 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $n = (($x * 7 + $y * 13 + (($x * $y) % 5)) % 11)
        $v = 62 + ($n - 5) * 3
        Px $b $x $y (Col $v ($v + 1) ($v - 1) 255)
    }
}
# 油斑：三处（更暗、带一点高光边）
foreach ($s in @(@(4, 4, 3), @(10, 7, 3), @(7, 11, 2))) {
    $cx = [int]$s[0]; $cy = [int]$s[1]; $rad = [int]$s[2]
    for ($dy = -$rad; $dy -le $rad; $dy++) {
        for ($dx = -$rad; $dx -le $rad; $dx++) {
            if ([Math]::Abs($dx) + [Math]::Abs($dy) -gt $rad) { continue }
            $c = if (([Math]::Abs($dx) + [Math]::Abs($dy)) -ge $rad) { Col 30 26 22 255 } else { Col 44 36 28 255 }
            Px $b ($cx + $dx) ($cy + $dy) $c
        }
    }
}
Save $b (Join-Path $BlockDir 'oil_shale.png')

# ==================================================================== 4) 桶图标（重着色模板）

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

# ==================================================================== 5) 流体贴图 + 桶

$fluids = @(
    @{ name = 'crude_oil';  rgb = @( 40,  38,   8) },    # 原油：深橄榄黑
    @{ name = 'naphtha';    rgb = @(150, 155,  90) },    # 石脑油：黄绿
    @{ name = 'kerosene';   rgb = @(150, 125,  60) },    # 煤油：金黄
    @{ name = 'diesel';     rgb = @(125,  95,  45) },    # 柴油：琥珀褐
    @{ name = 'heavy_oil';  rgb = @( 65,  42,  20) },    # 重油：深褐
    @{ name = 'bitumen';    rgb = @( 16,  16,  28) },    # 沥青：蓝黑
    @{ name = 'ethylene';   rgb = @( 80, 150,  80) },    # 乙烯：草绿
    @{ name = 'propylene';  rgb = @( 90, 130, 180) },    # 丙烯：中蓝
    @{ name = 'benzene';    rgb = @(115, 110, 165) },    # 苯：蓝紫
    @{ name = 'deuterated_solvent'; rgb = @(138, 132, 180) }, # 氘代溶剂：灰紫
    @{ name = 'lubricant';  rgb = @(100,  65,  20) },    # 润滑油：深蜜
    @{ name = 'light_oil';  rgb = @(100,  85,  65) },    # 轻油：灰褐
    @{ name = 'hydrogen';   rgb = @(215, 238, 244) },    # 氢气：近白青
    @{ name = 'sour_gas';   rgb = @( 80, 115,  55) },    # 酸性气：橄榄绿
    @{ name = 'low_sulfur_oil'; rgb = @(185, 170,  95) },# 低硫油：浅柠檬
    @{ name = 'phenol';     rgb = @(228, 150, 160) },    # 苯酚：珊瑚粉
    @{ name = 'formaldehyde'; rgb = @(110, 155, 140) },  # 甲醛：青绿
    @{ name = 'mma';        rgb = @(155,  95, 145) },    # 甲基丙烯酸甲酯：洋红
    @{ name = 'tetrafluoroethylene'; rgb = @(135, 160, 180) }, # 四氟乙烯：冰蓝
    @{ name = 'caprolactam'; rgb = @(236, 232, 214) },   # 己内酰胺：象牙
    @{ name = 'butadiene';  rgb = @(185, 105,  70) },    # 丁二烯：橙红
    @{ name = 'styrene';    rgb = @(155, 130, 110) },    # 苯乙烯：蜜桃褐
    @{ name = 'acrylonitrile'; rgb = @( 95, 120, 135) }, # 丙烯腈：冷蓝灰
    @{ name = 'chloroprene'; rgb = @(184, 200, 148) },   # 氯丁二烯：橄榄绿
    @{ name = 'siloxane';   rgb = @(170, 170, 172) },    # 硅氧烷单体：浅灰
    @{ name = 'high_cetane_diesel'; rgb = @(156, 112, 42) },   # 高十六烷值柴油：深金
    @{ name = 'high_octane_gasoline'; rgb = @(196, 178, 92) }, # 高辛烷值汽油：浅稻黄
    @{ name = 'leaded_gasoline'; rgb = @(178, 146, 120) },     # 含铅汽油：蜜褐
    @{ name = 'aviation_kerosene'; rgb = @(172, 160, 82) },    # 航空煤油：金橄榄
    @{ name = 'petroleum_gas'; rgb = @(142, 168, 132) },      # 石油气：浅灰绿
    @{ name = 'rocket_fuel'; rgb = @(150, 196, 208) },         # 火箭燃料：冰蓝
    @{ name = 'reformate';  rgb = @(170, 160, 105) },          # 重整油：浅金
    @{ name = 'gasoline';   rgb = @(205, 190, 120) },          # 普通汽油：浅黄
    @{ name = 'tetranitromethane'; rgb = @(230, 220, 160) },   # 四硝基甲烷：淡黄
    @{ name = 'liquid_methane'; rgb = @(170, 220, 230) },      # 液甲烷：冰蓝
    @{ name = 'methalox';   rgb = @(120, 190, 225) },          # 甲烷氧火箭燃料：深冰蓝
    @{ name = 'hydrazine';  rgb = @(200, 220, 235) },          # 肼：无色偏蓝
    @{ name = 'udmh';       rgb = @(190, 200, 150) },          # 偏二甲肼：淡黄绿
    @{ name = 'dinitrogen_tetroxide'; rgb = @(200, 140, 90) }, # 四氧化二氮：红棕
    @{ name = 'hypergolic_fuel'; rgb = @(150, 120, 90) },      # 自燃火箭燃料：深褐
    # ---- 2026-11-11 批次 1
    @{ name = 'nitrobenzene';    rgb = @(196, 168,  72) },     # 硝基苯：淡金
    @{ name = 'nitrobenzene_monopropellant'; rgb = @(176, 132,  60) }, # 硝基苯推进剂：橙金
    @{ name = 'aluminized_kerosene'; rgb = @(132, 128, 120) }  # 铝化煤油浆：金属灰
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

Write-Host ("完成：平面物品 {0} 张（材料模板上色）、3D 物品 {1} 件、油砂岩方块 1 张、流体贴图 {2} 张、桶图标 {2} 张。" -f `
    $flatItems.Count, $block3d.Count, ($fluids.Count * 2))

# gen_bio_materials.ps1 —— 生成生化线的物品图标与流体贴图（《生化线》B0 ①「物质注册与贴图规范」）。
#
# 口径（2026-10-15 改版，与石化线 gen_petro_materials.ps1 统一）：
#   · **物品 = 两条路子**：
#       ① 形状对得上的 → 用 `模板/材料/浅色` 的**灰度材料模板**（粒 nugget / 粉 dust / 锭 ingot /
#          板 plate / 齿轮 gear）按目标基色上色 —— 保留模板自带的立体高光与阴影；
#       ② 形状做不出来的（培养皿 / 药片）→ 走 **3D 立方体模型**（parent = minecraft:block/block），
#          各面直接复用**原版方块贴图**，不新增贴图素材。
#   · 流体：统一取原版水动画条换色（tools/lib_water_fluid.ps1）→ block/<name>_still.png（16×512）+ block/<name>_flow.png（32×1024），各带 .mcmeta；
#   · 桶：以**原版水桶贴图**为模板（tools/templates/water_bucket.png），只把蓝色水重着色成流体色。
#
# 输出：
#   src/main/resources/assets/stellar_evolution/textures/item/<item>.png        （平面件）
#   src/main/resources/assets/stellar_evolution/models/item/<item>.json         （平面：item/generated；3D：block/block + elements）
#   src/main/resources/assets/stellar_evolution/items/<item>.json               （物品模型定义）
#   src/main/resources/assets/stellar_evolution/textures/block/<fluid>_{still,flow}.png
#   src/main/resources/assets/stellar_evolution/textures/item/<fluid>_bucket.png
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_bio_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot = Split-Path -Parent $PSScriptRoot
$TexRoot  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir  = Join-Path $TexRoot 'item'
$BlockDir = Join-Path $TexRoot 'block'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $BlockDir, $ModelsDir, $ItemsDir | Out-Null
. (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')

function New-Bmp([int]$w, [int]$h) {
    return New-Object System.Drawing.Bitmap($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Col([int]$r, [int]$g, [int]$b, [int]$a) {
    return [System.Drawing.Color]::FromArgb($a, [Math]::Max(0, [Math]::Min(255, $r)), [Math]::Max(0, [Math]::Min(255, $g)), [Math]::Max(0, [Math]::Min(255, $b)))
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
# 模板原有的倒角 / 高光 / 阴影关系全部保留，只换色相。

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
    @{ id = 'biomass';            shape = 'nugget'; rgb = @( 74, 108,  52) },  # 生物质：深绿纤维团
    @{ id = 'lignocellulose';     shape = 'dust';   rgb = @(196, 170, 110) },  # 木质纤维：浅棕木屑
    @{ id = 'mineral_salt';       shape = 'dust';   rgb = @(246, 244, 248) },  # 矿物盐：近白盐粒
    @{ id = 'dried_enzyme';       shape = 'dust';   rgb = @(198, 146,  62) },  # 固体酶：琥珀颗粒
    @{ id = 'amino_powder';       shape = 'dust';   rgb = @(250, 246, 220) },  # 氨基酸粉：淡黄粉
    @{ id = 'protein_powder';     shape = 'dust';   rgb = @(244, 240, 232) },  # 蛋白粉：乳白粉
    @{ id = 'bioplastic_pellets'; shape = 'ingot'; rgb = @(138, 214, 194) },  # 生物塑料：青绿（统一锭模板）
    @{ id = 'dried_strain';       shape = 'dust';   rgb = @(188, 214, 160) },  # 干燥菌粉：淡绿粉
    @{ id = 'organic_fertilizer'; shape = 'nugget'; rgb = @( 96,  74,  52) },  # 有机肥：深褐颗粒
    @{ id = 'high_yield_strain';        shape = 'dust'; rgb = @(120, 220, 130) }, # 高产菌种：亮绿菌粉
    @{ id = 'high_yield_enzyme_strain'; shape = 'dust'; rgb = @(214, 190,  92) }, # 高产酶菌种：琥珀菌粉
    @{ id = 'extremophile_strain';      shape = 'dust'; rgb = @( 86, 200, 190) }, # 极端环境菌种：青绿菌粉
    @{ id = 'chiral_intermediate';      shape = 'dust'; rgb = @(206, 168, 236) }, # 手性中间体：淡紫粉
    @{ id = 'radioactive_tracer';       shape = 'nugget'; rgb = @(168, 236, 132) } # 放射性示踪剂：荧光绿
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

    # 菌种：培养皿（浅灰皿体 + 四面皿壁 + 白色琼脂 + 三粒绿菌落）
    'culture_strain' = @{
        textures = @{ dish = 'minecraft:block/light_gray_concrete'; agar = 'minecraft:block/white_concrete'; colony = 'minecraft:block/green_concrete' }
        boxes = @(
            @{ from = @(2, 3, 2);        to = @(14, 5, 14);       tex = 'dish' },
            @{ from = @(2, 5, 2);        to = @(14, 8, 3.4);      tex = 'dish' },
            @{ from = @(2, 5, 12.6);     to = @(14, 8, 14);       tex = 'dish' },
            @{ from = @(2, 5, 3.4);      to = @(3.4, 8, 12.6);    tex = 'dish' },
            @{ from = @(12.6, 5, 3.4);   to = @(14, 8, 12.6);     tex = 'dish' },
            @{ from = @(3.4, 5, 3.4);    to = @(12.6, 6, 12.6);   tex = 'agar' },
            @{ from = @(5, 6, 5);        to = @(7, 6.4, 7);       tex = 'colony' },
            @{ from = @(9, 6, 8);        to = @(10.6, 6.4, 9.6);  tex = 'colony' },
            @{ from = @(6.5, 6, 9.5);    to = @(7.6, 6.4, 10.6);  tex = 'colony' }
        )
    }

    # 维生素：双层药片（米黄上半 + 白下半，边缘带一圈深色腰线）
    'vitamins' = @{
        textures = @{ top = 'minecraft:block/yellow_terracotta'; bottom = 'minecraft:block/white_concrete'; line = 'minecraft:block/brown_terracotta' }
        boxes = @(
            @{ from = @(4.5, 4.5, 4.5);  to = @(11.5, 7.6, 11.5);  tex = 'bottom' },
            @{ from = @(4.5, 7.6, 4.5);  to = @(11.5, 8, 11.5);    tex = 'line' },
            @{ from = @(4.5, 8, 4.5);    to = @(11.5, 11.5, 11.5); tex = 'top' }
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

# ==================================================================== 3) 桶图标（重着色模板）

# 桶图标：**用原版水桶贴图当模板**（tools/templates/water_bucket.png，取自 MC 客户端 jar），
# 只把「蓝色水」像素按明暗重着色成目标流体色；桶身 / 描边 / 高光原样保留（不自己画）。
$BucketTemplate = Join-Path $PSScriptRoot 'templates\water_bucket.png'

function New-BucketIcon([int]$r, [int]$g, [int]$bl) {
    $src = [System.Drawing.Bitmap]::FromFile($BucketTemplate)
    $w = $src.Width; $h = $src.Height

    # 第一遍：找「水」像素（明显偏蓝）的亮度区间
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

    # 第二遍：水像素 → 目标流体色（保留明暗坡：最暗 0.6、最亮 1.0）；其余原样拷贝。
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

# ==================================================================== 4) 流体贴图

$fluids = @(
    @{ name = 'biomass_slurry';     rgb = @(107, 127, 58) },   # 生物质浆：褐绿
    @{ name = 'hydrolysate';        rgb = @(169, 118, 47) },   # 水解糖液：琥珀棕
    @{ name = 'fermentation_broth'; rgb = @(185, 163, 107) },  # 发酵液：浑浊米黄
    @{ name = 'ethanol';            rgb = @(198, 219, 238) },  # 乙醇：近透明淡蓝白
    @{ name = 'acetic_acid';        rgb = @(203, 188, 141) },  # 醋酸：淡黄褐
    @{ name = 'biogas';             rgb = @(152, 166, 76) },   # 沼气：黄绿
    @{ name = 'nutrient_medium';    rgb = @(222, 199, 124) },  # 培养基：淡黄
    @{ name = 'sterile_medium';     rgb = @(232, 214, 148) },  # 无菌培养基：浅金
    @{ name = 'enzyme_solution';    rgb = @(141, 189, 120) },  # 酶溶液：淡绿
    @{ name = 'protein_solution';   rgb = @(230, 222, 204) },  # 蛋白溶液：乳白
    @{ name = 'antibiotics';        rgb = @(125, 189, 190) },  # 抗生素：淡青
    # ---- E1 新增 7 条
    @{ name = 'sugar_liquor';       rgb = @(214, 178, 96) },   # 糖液：琥珀糖色
    @{ name = 'lactic_acid';        rgb = @(226, 226, 216) },  # 乳酸：近乳白
    @{ name = 'acetone';            rgb = @(232, 236, 228) },  # 丙酮：近透明淡灰
    @{ name = 'butanol';            rgb = @(226, 214, 176) },  # 丁醇：淡琥珀
    @{ name = 'glycerol';           rgb = @(238, 232, 220) },  # 甘油：稠白
    @{ name = 'amino_acid_solution';rgb = @(198, 186, 146) },  # 氨基酸液：米褐
    @{ name = 'biodiesel';          rgb = @(206, 176, 96) }    # 生物柴油：金黄
)

foreach ($f in $fluids) {
    $r = [int]$f.rgb[0]; $g = [int]$f.rgb[1]; $bl = [int]$f.rgb[2]

    # 原版水动画条换色（32 帧 still + 32 帧 flow，各带 mcmeta）
    Write-WaterFluidTextures -BlockDir $BlockDir -Name $f.name -Rgb @($r, $g, $bl) -ProjRoot $ProjRoot

    # 桶：图标 + 物品模型本体 + 物品模型定义（1.21.4+ 要两份）
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

Write-Host ("完成：平面物品 {0} 张（材料模板上色）、3D 物品 {1} 件、流体贴图 {2} 张、桶图标 {3} 张。" -f `
    $flatItems.Count, $block3d.Count, ($fluids.Count * 2), $fluids.Count)

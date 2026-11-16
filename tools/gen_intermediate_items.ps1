# gen_intermediate_items.ps1 —— 生成「中间材料（第二批）」的 12 件物品图标。
#
# 口径（照 gen_petro_materials.ps1 / gen_component_models.ps1）：
#   ① 形状对得上材料模板的 4 件（钢锭 ingot / 钢板 plate / 钢杆 rod / 钢齿轮 gear）→
#      取 `模板/材料/浅色/material_<shape>.png`，按模板自身亮度范围归一化后按**钢灰色**上色；
#   ② 形状做不出来的 8 件（轴承 / 机械框架 / 电阻 / 电容 / 传感器 / 伺服机构 / 温控器 / 控制模块）→
#      走 **3D 立方体模型**（parent = minecraft:block/block），各面直接复用**原版方块贴图**，不新增素材。
#
# 输出：
#   src/main/resources/assets/stellar_evolution/textures/item/<id>.png           （仅平面件）
#   src/main/resources/assets/stellar_evolution/models/item/<id>.json            （两种件都出）
#   src/main/resources/assets/stellar_evolution/items/<id>.json                  （物品模型定义）
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_intermediate_items.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$TexRoot   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures'
$ItemDir   = Join-Path $TexRoot 'item'
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$TplLight  = Join-Path $ProjRoot '模板\材料\浅色'
New-Item -ItemType Directory -Force -Path $ItemDir, $ModelsDir, $ItemsDir | Out-Null

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

# ==================================================================== 1) 材料模板上色（钢系平面件）

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

# 钢基色（中性冷灰）：模板自带的高光 / 阴影层次全部保留
$STEEL = @(170, 174, 181)

$flatItems = @(
    @{ id = 'steel_ingot'; shape = 'ingot' },  # 钢锭
    @{ id = 'steel_plate'; shape = 'plate' },  # 钢板
    @{ id = 'steel_rod';   shape = 'rod'   },  # 钢杆
    @{ id = 'steel_gear';  shape = 'gear'  }   # 钢齿轮
)

foreach ($it in $flatItems) {
    $bmp = New-TintedItem $it.shape $STEEL[0] $STEEL[1] $STEEL[2]
    Save $bmp (Join-Path $ItemDir ("{0}.png" -f $it.id))
    Write-FlatItemModel $it.id
    Write-Host ("  {0}.json（models/item + items，模板={1}）" -f $it.id, $it.shape)
}

# ==================================================================== 2) 3D 物品模型（8 件）

$FACES = 'down', 'up', 'north', 'south', 'west', 'east'

function New-Element($from, $to, $tex) {
    $faceObj = New-Object System.Management.Automation.PSObject
    foreach ($f in $FACES) {
        $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value ([pscustomobject]@{ texture = "#$tex" })
    }
    return [pscustomobject]@{ from = $from; to = $to; faces = $faceObj }
}

$block3d = @{

    # 轴承：外圈（钢）+ 内圈轮毂（铜）+ 四颗滚珠（黄铜）
    'bearing' = @{
        textures = @{ body = 'minecraft:block/iron_block'; cu = 'minecraft:block/copper_block'; ball = 'minecraft:block/gold_block' }
        boxes = @(
            @{ from = @(2, 10, 2);     to = @(14, 12, 14);   tex = 'body' },
            @{ from = @(2, 4, 2);      to = @(14, 6, 14);    tex = 'body' },
            @{ from = @(2, 6, 2);      to = @(4, 10, 14);    tex = 'body' },
            @{ from = @(12, 6, 2);     to = @(14, 10, 14);   tex = 'body' },
            @{ from = @(6, 6, 6);      to = @(10, 10, 10);   tex = 'cu' },
            @{ from = @(4.6, 7.2, 7.2); to = @(6.4, 9, 9);   tex = 'ball' },
            @{ from = @(9.6, 7.2, 7.2); to = @(11.4, 9, 9);  tex = 'ball' },
            @{ from = @(7.2, 7.2, 4.6); to = @(9, 9, 6.4);   tex = 'ball' },
            @{ from = @(7.2, 7.2, 9.6); to = @(9, 9, 11.4);  tex = 'ball' }
        )
    }

    # 机械框架：四角立柱（钢）+ 上下两圈横梁（铜）
    'machine_frame' = @{
        textures = @{ body = 'minecraft:block/iron_block'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(2, 2, 2);      to = @(4, 14, 4);     tex = 'body' },
            @{ from = @(12, 2, 2);     to = @(14, 14, 4);    tex = 'body' },
            @{ from = @(2, 2, 12);     to = @(4, 14, 14);    tex = 'body' },
            @{ from = @(12, 2, 12);    to = @(14, 14, 14);   tex = 'body' },
            @{ from = @(2, 12, 4);     to = @(14, 14, 12);   tex = 'cu' },
            @{ from = @(2, 2, 4);      to = @(14, 4, 12);    tex = 'cu' },
            @{ from = @(4, 12, 2);     to = @(12, 14, 4);    tex = 'cu' },
            @{ from = @(4, 2, 2);      to = @(12, 4, 4);     tex = 'cu' },
            @{ from = @(4, 12, 12);    to = @(12, 14, 14);   tex = 'cu' },
            @{ from = @(4, 2, 12);     to = @(12, 4, 14);    tex = 'cu' }
        )
    }

    # 精密重型框架：几何照机械框架，材质换下界合金（立柱）+ 钨块（横梁）
    'precision_heavy_frame' = @{
        textures = @{ body = 'minecraft:block/netherite_block'; cu = 'stellar_evolution:block/tungsten_block' }
        boxes = @(
            @{ from = @(2, 2, 2);      to = @(4, 14, 4);     tex = 'body' },
            @{ from = @(12, 2, 2);     to = @(14, 14, 4);    tex = 'body' },
            @{ from = @(2, 2, 12);     to = @(4, 14, 14);    tex = 'body' },
            @{ from = @(12, 2, 12);    to = @(14, 14, 14);   tex = 'body' },
            @{ from = @(2, 12, 4);     to = @(14, 14, 12);   tex = 'cu' },
            @{ from = @(2, 2, 4);      to = @(14, 4, 12);    tex = 'cu' },
            @{ from = @(4, 12, 2);     to = @(12, 14, 4);    tex = 'cu' },
            @{ from = @(4, 2, 2);      to = @(12, 4, 4);     tex = 'cu' },
            @{ from = @(4, 12, 12);    to = @(12, 14, 14);   tex = 'cu' },
            @{ from = @(4, 2, 12);     to = @(12, 4, 14);    tex = 'cu' }
        )
    }

    # 电阻：立式黑瓷体 + 两道色环 + 两端铜引脚
    'resistor' = @{
        textures = @{ body = 'minecraft:block/black_concrete'; band = 'minecraft:block/light_gray_concrete'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(6, 3, 6);      to = @(10, 13, 10);   tex = 'body' },
            @{ from = @(6, 6, 6);      to = @(10, 7, 10);    tex = 'band' },
            @{ from = @(6, 9.6, 6);    to = @(10, 10.6, 10); tex = 'band' },
            @{ from = @(7.2, 1, 7.2);  to = @(8.8, 3, 8.8);  tex = 'cu' },
            @{ from = @(7.2, 13, 7.2); to = @(8.8, 15, 8.8); tex = 'cu' }
        )
    }

    # 电容：灰罐体 + 铜色顶盖 / 底箍 + 引脚
    'capacitor' = @{
        textures = @{ body = 'minecraft:block/gray_concrete'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(5, 4, 5);      to = @(11, 13, 11);   tex = 'body' },
            @{ from = @(4.5, 13, 4.5); to = @(11.5, 14.4, 11.5); tex = 'cu' },
            @{ from = @(4.5, 2.6, 4.5); to = @(11.5, 4, 11.5); tex = 'cu' },
            @{ from = @(7.2, 1, 7.2);  to = @(8.8, 2.6, 8.8); tex = 'cu' }
        )
    }

    # 传感器：钢壳体 + 淡蓝探头镜片 + 铜底座
    'sensor' = @{
        textures = @{ body = 'minecraft:block/iron_block'; lens = 'minecraft:block/light_blue_stained_glass'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(4, 5, 4);      to = @(12, 11, 12);   tex = 'body' },
            @{ from = @(5.5, 11, 5.5); to = @(10.5, 12.4, 10.5); tex = 'lens' },
            @{ from = @(6, 2, 6);      to = @(10, 5, 10);    tex = 'cu' }
        )
    }

    # 伺服机构：钢机体 + 铜端盖 + 输出轴 + 黄铜输出齿轮
    'servo' = @{
        textures = @{ body = 'minecraft:block/iron_block'; cu = 'minecraft:block/copper_block'; gear = 'minecraft:block/gold_block' }
        boxes = @(
            @{ from = @(3, 5, 4);      to = @(11, 11, 12);   tex = 'body' },
            @{ from = @(11, 5.4, 4.4); to = @(12, 10.6, 11.6); tex = 'cu' },
            @{ from = @(12, 7.2, 7.2); to = @(13.4, 8.8, 8.8); tex = 'body' },
            @{ from = @(13.4, 5.4, 5.4); to = @(14.6, 10.6, 10.6); tex = 'gear' }
        )
    }

    # 温控器：浅灰壳体 + 黑表盘 + 铜毛细管探头
    'thermostat' = @{
        textures = @{ body = 'minecraft:block/light_gray_concrete'; dial = 'minecraft:block/black_concrete'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(4, 5, 5);      to = @(12, 11, 11);   tex = 'body' },
            @{ from = @(6, 6, 4.4);    to = @(10, 10, 5);    tex = 'dial' },
            @{ from = @(7.2, 2.4, 7.2); to = @(8.8, 5, 8.8); tex = 'cu' },
            @{ from = @(6.4, 1, 6.4);  to = @(9.6, 2.6, 9.6); tex = 'cu' }
        )
    }

    # 控制模块：钢底板 + 主芯片 + 指示灯 + 两侧接线端子
    'control_module' = @{
        textures = @{ body = 'minecraft:block/iron_block'; dark = 'minecraft:block/black_concrete'; light = 'minecraft:block/light_blue_concrete'; cu = 'minecraft:block/copper_block' }
        boxes = @(
            @{ from = @(3, 4, 4);      to = @(13, 7, 12);    tex = 'body' },
            @{ from = @(5.5, 7, 6);    to = @(10.5, 10.5, 10); tex = 'dark' },
            @{ from = @(3.6, 7, 4.6);  to = @(4.8, 8, 5.8);  tex = 'light' },
            @{ from = @(3, 7, 10.4);   to = @(4.4, 9, 11.6); tex = 'cu' },
            @{ from = @(11.6, 7, 4.4); to = @(13, 9, 5.6);   tex = 'cu' }
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

Write-Host ("完成：平面物品 {0} 张（材料模板上色）、3D 物品 {1} 件。" -f $flatItems.Count, $block3d.Count)

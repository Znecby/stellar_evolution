$ErrorActionPreference = "Stop"
$script:texmap = @{}
$assets = "c:\MCmod项目\stellar_evolution\src\main\resources\assets\stellar_evolution"
foreach ($d in @("$assets\models\block", "$assets\blockstates", "$assets\items")) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Force -Path $d | Out-Null }
}

function Tex($name) { $script:texmap[$name] = "minecraft:block/$name"; return "#$name" }

# UV 防拉伸口径（开发文档 §45 硬约束）：按 min(边长, 16) 取贴图中心区域。
# 32³ 大模型坐标会到 32，缺省 UV（= 几何坐标）会超出 0..16 → 贴图乱码 / 拉伸。
function Uv-Pair($a1, $a2) {
    $size = [double]$a2 - [double]$a1
    $len = [Math]::Min($size, 16.0)
    $start = (16.0 - $len) / 2.0
    return @( $start, ($start + $len) )
}

# 每个面按几何尺寸显式给 UV：down/up 用 (x,z)，north/south 用 (x,y)，west/east 用 (z,y)。
function Build-Faces($from, $to, $tex) {
    $xu = Uv-Pair $from[0] $to[0]
    $yv = Uv-Pair $from[1] $to[1]
    $zu = Uv-Pair $from[2] $to[2]
    $f = @{}
    $f["down"]  = @{ texture = $tex; uv = @($xu[0], $zu[0], $xu[1], $zu[1]) }
    $f["up"]    = @{ texture = $tex; uv = @($xu[0], $zu[0], $xu[1], $zu[1]) }
    $f["north"] = @{ texture = $tex; uv = @($xu[0], $yv[0], $xu[1], $yv[1]) }
    $f["south"] = @{ texture = $tex; uv = @($xu[0], $yv[0], $xu[1], $yv[1]) }
    $f["west"]  = @{ texture = $tex; uv = @($zu[0], $yv[0], $zu[1], $yv[1]) }
    $f["east"]  = @{ texture = $tex; uv = @($zu[0], $yv[0], $zu[1], $yv[1]) }
    return $f
}

# 元素一律轴对齐（不做逐元素 rotation），这样整机可以按朝向预旋转 4 份模型。
function Add-El($from, $to, $tex) {
    [void]$script:elems.Add([ordered]@{ from = $from; to = $to; tex = $tex; faces = (Build-Faces $from $to $tex) })
}

# 2×2×2 大机器的铜框架：12 条棱（与生化 / 石化机器同款口径）
function Add-Frame($tex) {
    $t = 1.25
    $e = 16.0
    # 4 根立柱
    Add-El @(0, 0, 0) @($t, $e, $t) $tex
    Add-El @(($e - $t), 0, 0) @($e, $e, $t) $tex
    Add-El @(0, 0, ($e - $t)) @($t, $e, $e) $tex
    Add-El @(($e - $t), 0, ($e - $t)) @($e, $e, $e) $tex
    # 底 / 顶，沿 X 的四条
    Add-El @($t, 0, 0) @(($e - $t), $t, $t) $tex
    Add-El @($t, 0, ($e - $t)) @(($e - $t), $t, $e) $tex
    Add-El @($t, ($e - $t), 0) @(($e - $t), $e, $t) $tex
    Add-El @($t, ($e - $t), ($e - $t)) @(($e - $t), $e, $e) $tex
    # 底 / 顶，沿 Z 的四条
    Add-El @(0, 0, $t) @($t, $t, ($e - $t)) $tex
    Add-El @(($e - $t), 0, $t) @($e, $t, ($e - $t)) $tex
    Add-El @(0, ($e - $t), $t) @($t, $e, ($e - $t)) $tex
    Add-El @(($e - $t), ($e - $t), $t) @($e, $e, ($e - $t)) $tex
}

# 16³ 元素 → 32³（2×2×2 大机器）；UV 按缩放后几何重算（防拉伸口径）
function Scale-El($el) {
    $from = @((([double]$el.from[0]) * 2), (([double]$el.from[1]) * 2), (([double]$el.from[2]) * 2))
    $to   = @((([double]$el.to[0]) * 2), (([double]$el.to[1]) * 2), (([double]$el.to[2]) * 2))
    return ,([ordered]@{
        from  = $from
        to    = $to
        tex   = $el.tex
        faces = (Build-Faces $from $to $el.tex)
    })
}

# 绕整机中心 (16,16,16) 顺时针转 $yaw（俯视），与 blockstate 的 facing 对应；
# UV 按旋转后几何重算（每面 UV 轴随朝向变化，不能沿用旧值）
function Rotate-El($el, $yaw) {
    $x1 = [double]$el.from[0]; $y1 = [double]$el.from[1]; $z1 = [double]$el.from[2]
    $x2 = [double]$el.to[0];   $y2 = [double]$el.to[1];   $z2 = [double]$el.to[2]
    switch ([int]$yaw) {
        0   { $nx1 = $x1; $nx2 = $x2; $nz1 = $z1; $nz2 = $z2 }
        90  { $nx1 = 32 - $z2; $nx2 = 32 - $z1; $nz1 = $x1; $nz2 = $x2 }
        180 { $nx1 = 32 - $x2; $nx2 = 32 - $x1; $nz1 = 32 - $z2; $nz2 = 32 - $z1 }
        270 { $nx1 = $z1; $nx2 = $z2; $nz1 = 32 - $x2; $nz2 = 32 - $x1 }
    }
    $from = @($nx1, $y1, $nz1)
    $to   = @($nx2, $y2, $nz2)
    return ,([ordered]@{
        from  = $from
        to    = $to
        faces = (Build-Faces $from $to $el.tex)
    })
}

function Write-Model($id, $elements) {
    $texturesForModel = @{ particle = "minecraft:block/iron_block" }
    foreach ($k in $script:texmap.Keys) { $texturesForModel[$k] = $script:texmap[$k] }

    # ---- 方块模型：32³，四个朝向各一份（几何预旋转，blockstate 不再用 y 旋转）
    $scaled = @()
    foreach ($e in $elements) { $scaled += Scale-El $e }

    foreach ($pair in @(@('north', 0), @('east', 90), @('south', 180), @('west', 270))) {
        $dir = [string]$pair[0]; $yaw = [int]$pair[1]
        $rotated = @()
        foreach ($e in $scaled) { $rotated += Rotate-El $e $yaw }
        $model = [ordered]@{
            parent   = "minecraft:block/block"
            textures = $texturesForModel
            elements = $rotated
        }
        [System.IO.File]::WriteAllText("$assets\models\block\${id}_${dir}.json",
            ($model | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))
    }

    # ---- 物品模型：与生化 / 石化 / 裂变大机器同一口径 —— 32³ 整机、默认 display（图标观感统一）
    $itemElements = @()
    foreach ($e in $scaled) { $itemElements += [ordered]@{ from = $e.from; to = $e.to; faces = $e.faces } }
    $itemModel = [ordered]@{
        parent   = "minecraft:block/block"
        textures = $texturesForModel
        elements = $itemElements
    }
    [System.IO.File]::WriteAllText("$assets\models\block\${id}_item.json",
        ($itemModel | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))

    # ---- blockstate：multipart = 4 朝向主模型 + 12 个机械管接点（铜垫，复用裂变线端口模型）
    # false = 未接管（open：铜环 + 深孔）；true = 已接管（capped：一块铜垫）
    $parts = @()
    foreach ($dir in @('north', 'east', 'south', 'west')) {
        $parts += [ordered]@{
            when  = [ordered]@{ facing = $dir }
            apply = [ordered]@{ model = "stellar_evolution:block/${id}_${dir}" }
        }
    }
    foreach ($port in @('north_a', 'north_b', 'east_a', 'east_b', 'south_a', 'south_b',
                       'west_a', 'west_b', 'down_a', 'down_b', 'down_c', 'down_d')) {
        $parts += [ordered]@{
            when  = [ordered]@{ "port_$port" = "false" }
            apply = [ordered]@{ model = "stellar_evolution:block/ore_leacher_port_${port}_open" }
        }
        $parts += [ordered]@{
            when  = [ordered]@{ "port_$port" = "true" }
            apply = [ordered]@{ model = "stellar_evolution:block/ore_leacher_port_${port}_capped" }
        }
    }
    $bs = [ordered]@{ multipart = $parts }
    [System.IO.File]::WriteAllText("$assets\blockstates\$id.json",
        ($bs | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

    $item = @{ model = @{ type = "minecraft:model"; model = "stellar_evolution:block/${id}_item" } }
    [System.IO.File]::WriteAllText("$assets\items\$id.json",
        ($item | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding($false)))
}

# =====================================================================
# 1) industrial_combustion_engine —— V16 柴油发电机组（2×2×2）
# =====================================================================
$iron = Tex "iron_block"; $dark = Tex "polished_deepslate"; $copper = Tex "copper_block"
$black = Tex "black_concrete"; $gray = Tex "gray_concrete"; $light = Tex "light_gray_concrete"
$orange = Tex "orange_concrete"; $yellow = Tex "yellow_concrete"; $red = Tex "redstone_block"
$script:elems = New-Object System.Collections.ArrayList
Add-Frame $copper
# 底座 + 滑轨（底座收到铜框架内侧 1.25~14.75：与框架底面 y=0 共面但**不重叠**，
# 任意重叠 = 从下方看整片 z-fighting 条纹；靠 xz 错开消除，而不是靠几百分之一格的压进）
Add-El @(1.25, 0, 1.25) @(14.75, 1.2, 14.75) $dark
Add-El @(0.5, 0.2, 1.2) @(15.5, 0.8, 2.2) $copper
Add-El @(0.5, 0.2, 13.8) @(15.5, 0.8, 14.8) $copper
# 曲轴箱（底面压进底座 0.05，避免与底座顶面共面闪烁）
Add-El @(1.0, 1.15, 3.2) @(15.0, 6.2, 12.8) $iron
Add-El @(1.2, 1.4, 3.4) @(14.8, 2.2, 12.6) $black
# 左右两列缸体（V 形用三级台阶表现，避免逐元素旋转）
Add-El @(1.8, 5.6, 3.8) @(14.2, 6.8, 7.2) $gray
Add-El @(1.8, 6.8, 4.6) @(14.2, 8.0, 7.2) $gray
Add-El @(1.8, 8.0, 5.4) @(14.2, 9.4, 7.2) $gray
Add-El @(1.8, 5.6, 8.8) @(14.2, 6.8, 12.2) $gray
Add-El @(1.8, 6.8, 8.8) @(14.2, 8.0, 11.4) $gray
Add-El @(1.8, 8.0, 8.8) @(14.2, 9.4, 10.6) $gray
# 缸盖（底面压进缸体 0.02，避免共面闪烁）
Add-El @(2.0, 9.38, 5.6) @(14.0, 10.6, 7.2) $dark
Add-El @(2.0, 9.38, 8.8) @(14.0, 10.6, 10.4) $dark
# 每列 4 道缸套分隔条（外侧面）
foreach ($x in @(3.5, 6.5, 9.5, 12.5)) {
    # 底面压进缸体 0.05：与缸体底面同平面会让分隔条下沿闪
    Add-El @($x, 5.65, 3.7) @(($x + 0.5), 9.4, 4.0) $black
    Add-El @($x, 5.65, 12.0) @(($x + 0.5), 9.4, 12.3) $black
}
# 双涡轮增压器
Add-El @(3.0, 9.6, 7.6) @(5.4, 12.2, 10.0) $gray
Add-El @(10.6, 9.6, 7.6) @(13.0, 12.2, 10.0) $gray
Add-El @(3.4, 12.15, 8.0) @(5.0, 12.9, 9.6) $copper
Add-El @(11.0, 12.15, 8.0) @(12.6, 12.9, 9.6) $copper
# 中冷器：芯体在后（z 1.3~2.2），前面 5 道横向散热片凸出（z 0.95~1.3）。
# 注意：芯体前脸与散热片前脸**不能同平面**（异材质同向共面 = 从风扇口看进去整片闪），
# 这里靠 z 错开；散热片背面与芯体前脸是背靠背，安全。
Add-El @(1.4, 3.4, 1.3) @(14.6, 10.4, 2.2) $light
for ($i = 0; $i -lt 5; $i++) {
    $y = 3.9 + $i * 1.3
    Add-El @(1.8, $y, 0.95) @(14.2, ($y + 0.3), 1.3) $gray
}
# 前端风扇罩：只留外圈方框，中间镂空 —— 旋转风扇由 BER 画（industrial_engine_fan）
Add-El @(1.2, 2.4, 0.4) @(14.8, 3.4, 0.9) $black
Add-El @(1.2, 10.4, 0.4) @(14.8, 11.4, 0.9) $black
Add-El @(1.2, 3.4, 0.4) @(2.2, 10.4, 0.9) $black
Add-El @(13.8, 3.4, 0.4) @(14.8, 10.4, 0.9) $black
# 罩口四角铜螺栓
foreach ($p in @(@(1.6, 2.8), @(13.2, 2.8), @(1.6, 10.0), @(13.2, 10.0))) {
    Add-El @($p[0], $p[1], 0.2) @((($p[0]) + 0.8), (($p[1]) + 0.6), 0.4) $copper
}
# 排气歧管（左右各一条，末端烧红）
Add-El @(1.6, 8.6, 6.9) @(14.4, 9.35, 7.5) $black
Add-El @(1.6, 8.6, 8.5) @(14.4, 9.35, 9.1) $black
Add-El @(14.35, 8.5, 6.8) @(15.6, 9.5, 7.6) $orange
Add-El @(14.35, 8.5, 8.4) @(15.6, 9.5, 9.2) $orange
# 发电机端：铜机壳 + 散热鳍片
Add-El @(1.2, 1.8, 12.9) @(14.8, 8.8, 15.4) $copper
for ($i = 0; $i -lt 5; $i++) {
    $x = 2.2 + $i * 2.6
    Add-El @($x, 2.2, 15.35) @(($x + 1.6), 8.4, 15.7) $gray
}
Add-El @(5.6, 8.75, 13.4) @(10.4, 9.6, 14.8) $dark
# 燃油共轨 + 喷油管
Add-El @(2.0, 7.2, 7.4) @(14.0, 7.9, 8.6) $copper
foreach ($x in @(3.0, 6.0, 9.0, 12.0)) {
    Add-El @($x, 6.6, 7.6) @(($x + 0.4), 7.25, 8.4) $yellow
}
# 控制柜 + 红色急停
Add-El @(13.2, 1.8, 3.0) @(15.6, 6.4, 5.4) $dark
Add-El @(15.55, 4.6, 3.6) @(15.8, 5.2, 4.2) $red
# 地脚螺栓
foreach ($p in @(@(1.0, 0.9), @(14.4, 0.9), @(1.0, 15.0), @(14.4, 15.0))) {
    Add-El @($p[0], 1.15, $p[1]) @((($p[0]) + 0.6), 1.7, (($p[1]) + 0.6)) $light
}
# 顶部双立式排气管（末端铜口，顶部不再光秃）
foreach ($x in @(4.0, 10.4)) {
    Add-El @($x, 12.9, 8.2) @(($x + 1.6), 14.6, 9.8) $gray
    Add-El @(($x + 0.2), 14.6, 8.4) @((($x + 1.4)), 15.2, 9.6) $copper
}
Write-Model "industrial_combustion_engine" $script:elems.ToArray()

# =====================================================================
# 2) combined_cycle_gas_turbine —— 压气机 + 燃烧室 + 透平 + 余热锅炉
# =====================================================================
$script:elems = New-Object System.Collections.ArrayList
Add-Frame $copper
# 底座（同样收到铜框架内侧，避免与框架底面共面重叠 → 下方 z-fighting）
Add-El @(1.25, 0, 1.25) @(14.75, 1.2, 14.75) $dark
# 压气机机匣 + 4 道加强环
Add-El @(3.0, 3.0, 4.0) @(14.0, 11.0, 12.0) $iron
foreach ($x in @(3.2, 6.0, 8.8, 11.6)) {
    Add-El @($x, 2.8, 3.8) @(($x + 0.5), 11.2, 12.2) $dark
}
# 进气口：只留外框，中间是旋转的压气机叶轮（BER 画 gas_turbine_compressor）
Add-El @(12.6, 3.4, 4.4) @(15.6, 4.4, 11.6) $black
Add-El @(12.6, 8.55, 4.4) @(15.6, 9.55, 11.6) $black
Add-El @(12.6, 4.4, 4.4) @(15.6, 8.55, 5.4) $black
Add-El @(12.6, 4.4, 10.6) @(15.6, 8.55, 11.6) $black
# 叶轮背后的内衬底（深色，避免看穿到机匣）
Add-El @(12.6, 4.4, 5.4) @(13.05, 8.55, 10.6) $dark
# 燃烧室：环绕 6 个燃料罐
Add-El @(7.6, 4.4, 3.4) @(9.4, 6.0, 4.6) $orange
Add-El @(7.6, 8.0, 3.4) @(9.4, 9.6, 4.6) $orange
Add-El @(7.6, 4.4, 11.4) @(9.4, 6.0, 12.6) $orange
Add-El @(7.6, 8.0, 11.4) @(9.4, 9.6, 12.6) $orange
Add-El @(7.6, 2.85, 6.4) @(9.4, 3.8, 9.6) $yellow
Add-El @(7.6, 10.2, 6.4) @(9.4, 11.15, 9.6) $yellow
# 透平段 + 三级叶片
Add-El @(4.6, 3.4, 4.6) @(7.4, 10.6, 11.4) $gray
foreach ($x in @(4.8, 5.7, 6.6)) {
    Add-El @($x, 4.0, 4.2) @(($x + 0.35), 10.0, 11.8) $light
}
# 排气段（左面压进发电机端铜机壳 0.05，避免共面闪烁）
Add-El @(0.85, 3.8, 5.0) @(4.6, 10.2, 11.0) $dark
Add-El @(0.2, 4.4, 5.6) @(0.9, 9.55, 10.4) $black
# 顶部余热锅炉（卧式汽包；顶盖压进 0.05，避免共面闪烁）
Add-El @(3.4, 11.4, 5.0) @(12.6, 14.2, 11.0) $copper
Add-El @(4.0, 14.15, 5.6) @(12.0, 14.8, 10.4) $light
# 锅炉下降管 + 上升管（底面压进底座 0.05，避免共面闪烁）
Add-El @(4.4, 1.15, 6.4) @(5.0, 11.4, 7.0) $copper
Add-El @(11.0, 1.15, 9.0) @(11.6, 11.4, 9.6) $copper
# 水管（进水，浅灰）
Add-El @(12.8, 11.9, 10.2) @(15.4, 12.5, 10.8) $light
Add-El @(15.2, 11.9, 10.2) @(15.8, 12.5, 12.4) $light
# 燃料管（铜）+ 氧气管（浅色）
Add-El @(13.6, 10.2, 3.0) @(14.2, 10.8, 4.2) $copper
Add-El @(13.6, 2.0, 12.8) @(14.2, 2.6, 14.0) $light
# 发电机端（左侧铜机壳 + 鳍片）
Add-El @(0.8, 2.4, 3.0) @(3.0, 9.6, 13.0) $copper
for ($i = 0; $i -lt 4; $i++) {
    $z = 3.6 + $i * 2.4
    Add-El @(0.3, 2.8, $z) @(0.88, 9.2, ($z + 1.2)) $gray
}
# 控制柜（按钮压进柜体 0.05，避免共面闪烁）
Add-El @(0.8, 1.4, 0.8) @(3.2, 5.4, 2.8) $dark
Add-El @(3.15, 3.6, 1.4) @(3.4, 4.2, 2.0) $red
# 地脚螺栓
foreach ($p in @(@(1.0, 0.9), @(14.4, 0.9), @(1.0, 15.0), @(14.4, 15.0))) {
    Add-El @($p[0], 1.15, $p[1]) @((($p[0]) + 0.6), 1.7, (($p[1]) + 0.6)) $light
}
Write-Model "combined_cycle_gas_turbine" $script:elems.ToArray()

# =====================================================================
# 3) aerospace_rocket_engine —— 涡轮泵 + 燃烧室 + 钟形喷管（喷口朝北，z=0 出焰）
# =====================================================================
$script:elems = New-Object System.Collections.ArrayList
Add-Frame $copper
# 底座（与内燃机 / 燃气轮机同款：封底 + 收到铜框架内侧，避免与框架底面共面重叠 → 下方 z-fighting）
Add-El @(1.25, 0, 1.25) @(14.75, 1.2, 14.75) $dark
# 尾部：外框 + 内凹检修腔（不再是一整块平板，从后面看有纵深）
Add-El @(1.6, 1.6, 14.6) @(14.4, 2.8, 15.8) $dark
Add-El @(1.6, 13.2, 14.6) @(14.4, 14.4, 15.8) $dark
Add-El @(1.6, 2.8, 14.6) @(2.8, 13.2, 15.8) $dark
Add-El @(13.2, 2.8, 14.6) @(14.4, 13.2, 15.8) $dark
Add-El @(2.8, 2.8, 14.2) @(13.2, 13.2, 14.75) $black
foreach ($p in @(@(2.9, 2.9), @(12.5, 2.9), @(2.9, 12.5), @(12.5, 12.5))) {
    Add-El @($p[0], $p[1], 14.75) @((($p[0]) + 0.6), (($p[1]) + 0.6), 15.8) $copper
}
foreach ($p in @(@(2.2, 2.2), @(12.8, 2.2), @(2.2, 12.8), @(12.8, 12.8))) {
    Add-El @($p[0], $p[1], 13.8) @((($p[0]) + 1.0), (($p[1]) + 1.0), 14.4) $light
}
# 万向节 + 推力座（万向节顶面压进安装板 0.05，避免共面闪烁）
Add-El @(6.2, 6.2, 12.6) @(9.8, 9.8, 14.25) $copper
Add-El @(5.4, 5.4, 12.2) @(10.6, 10.6, 12.8) $gray
# 两台涡轮泵（燃料 / 液氧；泵盖各压进泵体 0.05，避免共面闪烁）
Add-El @(10.0, 8.8, 9.0) @(13.6, 12.4, 12.6) $gray
Add-El @(10.0, 3.6, 9.0) @(13.6, 7.2, 12.6) $light
Add-El @(10.4, 12.35, 9.4) @(13.2, 13.2, 12.2) $copper
Add-El @(10.4, 2.8, 9.4) @(13.2, 3.65, 12.2) $copper
# 泵出口管 → 喷注器
Add-El @(8.8, 9.6, 9.6) @(10.2, 10.4, 10.55) $copper
Add-El @(8.8, 5.6, 9.6) @(10.2, 6.4, 10.55) $light
# 燃烧室（铜）+ 4 条再生冷却槽
Add-El @(5.2, 5.2, 7.2) @(10.8, 10.8, 10.8) $copper
for ($i = 0; $i -lt 4; $i++) {
    $x = 5.4 + $i * 1.4
    Add-El @($x, 5.0, 7.4) @(($x + 0.5), 11.0, 10.6) $yellow
}
Add-El @(5.0, 5.0, 10.6) @(11.0, 11.0, 11.4) $dark
# 喷注器盘（两侧各压进邻件 0.05，避免与喷管 / 燃烧室共面闪烁）
Add-El @(5.4, 5.4, 6.55) @(10.6, 10.6, 7.25) $black
# 钟形喷管：五段扩张的「空心方锥」—— 段内只画四面壁，中间是通的，
# 从正面能看进喉部（背面被喷注器盘封住），不再是实心大方块。
$nozzleRings = @(
    @(0.0, 0.65, 2.0, 14.0, 3.3, 12.7, $black),
    @(0.6, 2.2, 2.6, 13.4, 3.9, 12.1, $dark),
    @(2.15, 4.0, 3.8, 12.2, 4.9, 11.1, $gray),
    @(4.0, 5.6, 4.8, 11.2, 5.7, 10.3, $gray),
    @(5.55, 6.6, 5.6, 10.4, 5.9, 10.1, $dark)
)
foreach ($r in $nozzleRings) {
    $z1 = $r[0]; $z2 = $r[1]; $o1 = $r[2]; $o2 = $r[3]; $i1 = $r[4]; $i2 = $r[5]; $tex = $r[6]
    Add-El @($o1, $o1, $z1) @($o2, $i1, $z2) $tex      # 下壁
    Add-El @($o1, $i2, $z1) @($o2, $o2, $z2) $tex      # 上壁
    Add-El @($o1, $i1, $z1) @($i1, $i2, $z2) $tex      # 西壁
    Add-El @($i2, $i1, $z1) @($o2, $i2, $z2) $tex      # 东壁
}
# 冷却箍（喷管外三圈，同样是四段环，不能堵住喷口）
foreach ($h in @(@(3.4, 2.4), @(4.4, 4.2), @(5.2, 5.4))) {
    $o = $h[0]; $z = $h[1]; $m = 16.0 - $o
    Add-El @($o, $o, $z) @($m, ($o + 0.55), ($z + 0.25)) $copper
    Add-El @($o, ($m - 0.55), $z) @($m, $m, ($z + 0.25)) $copper
    Add-El @($o, ($o + 0.55), $z) @(($o + 0.55), ($m - 0.55), ($z + 0.25)) $copper
    Add-El @(($m - 0.55), ($o + 0.55), $z) @($m, ($m - 0.55), ($z + 0.25)) $copper
}
# 阀门（红 = 燃料，浅色 = 液氧）
Add-El @(11.4, 11.4, 10.0) @(12.6, 12.6, 11.2) $red
Add-El @(11.4, 3.4, 10.0) @(12.6, 4.6, 11.2) $light
# 点火器（右面压进燃烧室 0.05，避免共面闪烁）
Add-El @(4.2, 7.4, 8.0) @(5.25, 8.4, 9.0) $orange
# 顶部小管路（液氧管底面压进燃烧室 0.05，避免共面闪烁）
Add-El @(6.4, 10.8, 10.4) @(8.2, 11.4, 11.2) $copper
Add-El @(6.4, 4.6, 10.4) @(8.2, 5.25, 11.2) $light
Write-Model "aerospace_rocket_engine" $script:elems.ToArray()

# =====================================================================
# 转子载体（隐藏方块，只给 client.EngineMachineRenderer 借模型用）
# 坐标一律 0..32（与整机同尺度），轴对齐；不写 display、无物品、不放置。
#   industrial_engine_fan         风扇：绕 Z 轴，枢轴 (16, 13.8, 1.2)
#   gas_turbine_compressor        压气机叶轮：绕 X 轴，枢轴 (27.0, 12.95, 16.0)
#   rocket_engine_turbopump       涡轮泵叶轮：绕 Y 轴，枢轴 (23.6, 24.8, 21.6)
# =====================================================================
function Write-Rotor($id, $elements) {
    $texturesForModel = @{ particle = "minecraft:block/iron_block" }
    foreach ($k in $script:texmap.Keys) { $texturesForModel[$k] = $script:texmap[$k] }
    $els = @()
    foreach ($e in $elements) { $els += [ordered]@{ from = $e.from; to = $e.to; faces = $e.faces } }
    $model = [ordered]@{ parent = "minecraft:block/block"; textures = $texturesForModel; elements = $els }
    [System.IO.File]::WriteAllText("$assets\models\block\$id.json",
        ($model | ConvertTo-Json -Depth 12), (New-Object System.Text.UTF8Encoding($false)))
    $bs = [ordered]@{ variants = [ordered]@{ "" = @{ model = "stellar_evolution:block/$id" } } }
    [System.IO.File]::WriteAllText("$assets\blockstates\$id.json",
        ($bs | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
}

# ---- 内燃机组前风扇（中心 16,13.8；半径约 6.6）
# 8 片叶：每片内外两段，外段在 z 上略抬 → 看得出迎风角；整体是「轮毂 + 放射叶」的扇叶相
$script:elems = New-Object System.Collections.ArrayList
Add-El @(13.6, 11.4, 0.9) @(18.4, 16.2, 1.8) $copper
# 东 / 西
Add-El @(18.0, 13.0, 0.95) @(21.6, 14.6, 1.55) $gray
Add-El @(21.4, 12.8, 1.15) @(24.6, 14.8, 1.75) $gray
Add-El @(10.4, 13.0, 0.95) @(14.0, 14.6, 1.55) $gray
Add-El @(7.4, 12.8, 1.15) @(10.6, 14.8, 1.75) $gray
# 北 / 南
Add-El @(15.2, 16.2, 0.95) @(16.8, 18.8, 1.55) $gray
Add-El @(15.0, 18.6, 1.15) @(17.0, 20.6, 1.75) $gray
Add-El @(15.2, 8.8, 0.95) @(16.8, 11.4, 1.55) $gray
Add-El @(15.0, 7.0, 1.15) @(17.0, 9.0, 1.75) $gray
# 四个斜向（阶梯近似 45°）
Add-El @(18.2, 15.8, 0.95) @(20.8, 18.4, 1.55) $gray
Add-El @(20.4, 18.0, 1.15) @(22.4, 20.0, 1.75) $gray
Add-El @(11.2, 15.8, 0.95) @(13.8, 18.4, 1.55) $gray
Add-El @(9.6, 18.0, 1.15) @(11.6, 20.0, 1.75) $gray
Add-El @(18.2, 9.2, 0.95) @(20.8, 11.8, 1.55) $gray
Add-El @(20.4, 7.8, 1.15) @(22.4, 9.8, 1.75) $gray
Add-El @(11.2, 9.2, 0.95) @(13.8, 11.8, 1.55) $gray
Add-El @(9.6, 7.8, 1.15) @(11.6, 9.8, 1.75) $gray
Write-Rotor "industrial_engine_fan" $script:elems.ToArray()

# ---- 燃气轮机压气机叶轮（x 必须落在机匣东面 28 之外，否则被压气机机匣整块挡住）
# 中心 (29.1, 12.95, 16)；绕 X 轴
$script:elems = New-Object System.Collections.ArrayList
Add-El @(28.2, 11.0, 14.2) @(30.1, 14.9, 17.8) $copper
Add-El @(28.5, 14.9, 15.0) @(29.9, 16.9, 17.0) $gray
Add-El @(28.5, 9.0, 15.0) @(29.9, 11.0, 17.0) $gray
Add-El @(28.5, 12.3, 17.0) @(29.9, 13.6, 20.6) $gray
Add-El @(28.5, 12.3, 11.4) @(29.9, 13.6, 15.0) $gray
Add-El @(28.5, 14.6, 17.2) @(29.9, 16.6, 19.2) $dark
Add-El @(28.5, 9.3, 17.2) @(29.9, 11.3, 19.2) $dark
Add-El @(28.5, 14.6, 12.8) @(29.9, 16.6, 14.8) $dark
Add-El @(28.5, 9.3, 12.8) @(29.9, 11.3, 14.8) $dark
Write-Rotor "gas_turbine_compressor" $script:elems.ToArray()

# ---- 火箭涡轮泵叶轮（中心 23.6,24.8,21.6；绕 Y 轴，顶面）
$script:elems = New-Object System.Collections.ArrayList
Add-El @(22.0, 24.6, 20.0) @(25.2, 25.8, 23.2) $copper
Add-El @(25.4, 24.8, 20.8) @(29.0, 25.6, 22.4) $gray
Add-El @(18.2, 24.8, 20.8) @(21.8, 25.6, 22.4) $gray
Add-El @(22.8, 24.8, 17.4) @(24.4, 25.6, 21.0) $gray
Add-El @(22.8, 24.8, 22.2) @(24.4, 25.6, 25.8) $gray
Write-Rotor "rocket_engine_turbopump" $script:elems.ToArray()

Write-Host "engine machine assets written (2x2x2, 4 facings + 3 rotors)"

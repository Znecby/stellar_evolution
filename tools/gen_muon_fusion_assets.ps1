# gen_muon_fusion_assets.ps1 —— 缪子聚变仪（muon_fusion）方块资源生成。
#
#   · 2×2×2 下界合金框架（12 根主梁 + 8 个垫脚，直接复用裂变线机器，几何/UV 完全一致）
#   · 固定不动的方块化足球核心仓：深色五边形 + 钨色六边形，格面带高度差浮雕
#   · 6 个方向的重型夹持板（法兰 + 面板）把核心仓"关押"在框架内
#   · 18 颗插栓各有一个隐藏载体方块，由渲染器做错相的"深入 / 抽出"动画
#   · 框架 8 个内角各有一条阶梯式斜撑连到球壳，避免核心仓看起来悬浮
#   · 只生成外观资源，不涉及机制 / 配方
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_muon_fusion_assets.ps1
$ErrorActionPreference = 'Stop'
[Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

$root   = Split-Path -Parent $PSScriptRoot
$assets = Join-Path $root 'src\main\resources\assets\stellar_evolution'
$data   = Join-Path $root 'src\main\resources\data\stellar_evolution'
$utf8   = New-Object System.Text.UTF8Encoding($false)

$script:tex = [ordered]@{
    neth     = 'minecraft:block/netherite_block'
    tung     = 'stellar_evolution:block/tungsten_block'
    gold     = 'minecraft:block/gold_block'
    dark     = 'minecraft:block/polished_blackstone'
    copper   = 'minecraft:block/copper_block'
    iron     = 'minecraft:block/iron_block'
    particle = 'minecraft:block/netherite_block'
}

function Choose([bool]$condition, $whenTrue, $whenFalse) {
    if ($condition) { return $whenTrue }
    return $whenFalse
}

# 单面 UV 尺寸：1 个 UV 像素 ≈ 1 个模型像素；小于 1 的细节放大到 1，
# 大于 16 的长条压到 16（与框架同口径，避免采样到图集外）。
function Uv-Size([double]$value) {
    $size = [Math]::Max(1.0, [Math]::Min(16.0, $value))
    return [Math]::Round($size, 3)
}

function Add-Box($list, [double]$x1, [double]$y1, [double]$z1,
                 [double]$x2, [double]$y2, [double]$z2, [string]$tex, [double]$jitter = 0.0) {
    $dx = $x2 - $x1
    $dy = $y2 - $y1
    $dz = $z2 - $z1
    $ux = Uv-Size $dx
    $uy = Uv-Size $dy
    $uz = Uv-Size $dz
    # 在贴图中间区域内左右挪一点，让相邻格采到不同的像素（整片同色块会显得很"塑料"）
    $ox = [Math]::Round((16.0 - $ux) / 2.0 + $jitter * (16.0 - $ux) * 0.35, 3)
    $oy = [Math]::Round((16.0 - $uy) / 2.0 + $jitter * (16.0 - $uy) * 0.35, 3)
    $oz = [Math]::Round((16.0 - $uz) / 2.0 + $jitter * (16.0 - $uz) * 0.35, 3)
    # 先算好右 / 下边界：PowerShell 里逗号比 + 结合更紧，直接写 @($a, $b + $c) 会散成多个元素
    $ue = [Math]::Round($ox + $ux, 3)
    $ve = [Math]::Round($oy + $uy, 3)
    $we = [Math]::Round($oz + $uz, 3)
    $faces = [ordered]@{
        # 显式 UV：不写 uv 时，32 格模型会按图集坐标采样，出现串图。
        down  = [ordered]@{ texture = "#$tex"; uv = @($ox, $oz, $ue, $we) }
        up    = [ordered]@{ texture = "#$tex"; uv = @($ox, $oz, $ue, $we) }
        north = [ordered]@{ texture = "#$tex"; uv = @($ox, $oy, $ue, $ve) }
        south = [ordered]@{ texture = "#$tex"; uv = @($ox, $oy, $ue, $ve) }
        west  = [ordered]@{ texture = "#$tex"; uv = @($oz, $oy, $we, $ve) }
        east  = [ordered]@{ texture = "#$tex"; uv = @($oz, $oy, $we, $ve) }
    }
    $e = [ordered]@{
        from  = @($x1, $y1, $z1)
        to    = @($x2, $y2, $z2)
        faces = $faces
    }
    [void]$list.Add($e)
}

# 在某个轴向面（n = ±X/±Y/±Z）上放一个轴对齐长方体。
#   n  ：面法线（单位轴向量）；u / v：面内两个切向基（单位轴向量）
#   du / dv：沿切向的偏移；halfU / halfV：切向半宽；n1 / n2：沿法线的起止距离
function Add-PoleBox($list, [double[]]$n, [double[]]$u, [double[]]$v,
                     [double]$du, [double]$dv,
                     [double]$halfU, [double]$halfV,
                     [double]$n1, [double]$n2, [string]$tex) {
    $mid = ($n1 + $n2) / 2.0
    $hn  = ($n2 - $n1) / 2.0
    $cx  = 16.0 + $n[0] * $mid + $u[0] * $du + $v[0] * $dv
    $cy  = 16.0 + $n[1] * $mid + $u[1] * $du + $v[1] * $dv
    $cz  = 16.0 + $n[2] * $mid + $u[2] * $du + $v[2] * $dv
    $sx  = [Math]::Abs($n[0]) * $hn + [Math]::Abs($u[0]) * $halfU + [Math]::Abs($v[0]) * $halfV
    $sy  = [Math]::Abs($n[1]) * $hn + [Math]::Abs($u[1]) * $halfU + [Math]::Abs($v[1]) * $halfV
    $sz  = [Math]::Abs($n[2]) * $hn + [Math]::Abs($u[2]) * $halfU + [Math]::Abs($v[2]) * $halfV
    Add-Box $list ($cx - $sx) ($cy - $sy) ($cz - $sz) ($cx + $sx) ($cy + $sy) ($cz + $sz) $tex
}

function Save-Model([string]$name, $elements) {
    $model = [ordered]@{
        parent            = 'minecraft:block/block'
        ambientocclusion  = $false
        textures          = $script:tex
        elements          = $elements.ToArray()
    }
    $path = Join-Path $assets "models\block\$name.json"
    $json = $model | ConvertTo-Json -Depth 100
    [IO.File]::WriteAllText($path, $json, $utf8)
    Write-Host "model: $path"
}

function Save-Text([string]$path, [string]$text) {
    $dir = Split-Path -Parent $path
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    [IO.File]::WriteAllText($path, $text, $utf8)
    Write-Host "asset: $path"
}

# --------------------------------------------------------------------------- 核心
# 6 个夹持板方向：n = 面法线，u / v = 面内切向基。
# 顺序必须与 Java 端 FissionMachineStructure.muonBolts() 一致（+X -X +Y -Y +Z -Z，每面 3 颗）。
$script:plateFaces = @(
    @(@(1, 0, 0), @(0, 1, 0), @(0, 0, 1)),
    @(@(-1, 0, 0), @(0, 1, 0), @(0, 0, 1)),
    @(@(0, 1, 0), @(1, 0, 0), @(0, 0, 1)),
    @(@(0, -1, 0), @(1, 0, 0), @(0, 0, 1)),
    @(@(0, 0, 1), @(1, 0, 0), @(0, 1, 0)),
    @(@(0, 0, -1), @(1, 0, 0), @(0, 1, 0))
)

# 18 颗插栓的位姿（每块夹持板 3 颗）→ 每颗一个隐藏载体方块，由渲染器做"深入 / 抽出"动画
function New-BoltSpecs {
    $specs = New-Object System.Collections.ArrayList
    $index = 0
    foreach ($f in $script:plateFaces) {
        for ($k = 0; $k -lt 3; $k++) {
            $angle = 1.5707963 + $k * 2.0943951
            [void]$specs.Add([pscustomobject]@{
                name = "muon_bolt_$index"
                n    = $f[0]
                u    = $f[1]
                v    = $f[2]
                du   = [Math]::Round([Math]::Cos($angle) * 2.6, 3)
                dv   = [Math]::Round([Math]::Sin($angle) * 2.6, 3)
            })
            $index++
        }
    }
    return $specs
}

# 单颗插栓：铜柱 + 金栓头 —— 会伸缩的就是插栓本身（不再额外加座圈）。
# 铜柱下端埋在夹持板里：抽到最外时根部仍在板内，金头顶端也不会顶出机壳上沿。
# 铜柱比金头粗，于是缩到最里时金头仍露在外面，四周还留着一圈铜。
function Add-BoltPiston($l, $spec) {
    Add-PoleBox $l $spec.n $spec.u $spec.v $spec.du $spec.dv 0.88 0.88 12.85 15.00 'copper'
    Add-PoleBox $l $spec.n $spec.u $spec.v $spec.du $spec.dv 0.60 0.60 15.00 15.45 'gold'
}

# 方块化足球球壳：深色五边形 + 钨色六边形。
# 每格缩进 0.1（格间留 0.2 的暗缝），再让每格采到贴图上不同的像素——
# 否则整颗球就是一排一模一样的纯色方块。
function Add-Shell($l) {
    $center = 16.0
    $step   = 2.4
    $radius = 13.2
    $inner  = $radius - $step
    $phi    = (1.0 + [Math]::Sqrt(5.0)) / 2.0

    # 12 个五边形中心（正二十面体顶点方向），扁平存 xyz
    $raw = @(
        0, 1, $phi, 0, 1, -$phi, 0, -1, $phi, 0, -1, -$phi,
        1, $phi, 0, 1, -$phi, 0, -1, $phi, 0, -1, -$phi, 0,
        $phi, 0, 1, $phi, 0, -1, -$phi, 0, 1, -$phi, 0, -1
    )
    $normals = @()
    for ($i = 0; $i -lt $raw.Count; $i += 3) {
        $len = [Math]::Sqrt($raw[$i] * $raw[$i] + $raw[$i + 1] * $raw[$i + 1] + $raw[$i + 2] * $raw[$i + 2])
        $normals += $raw[$i] / $len
        $normals += $raw[$i + 1] / $len
        $normals += $raw[$i + 2] / $len
    }

    $half = $step / 2.0 - 0.1
    $count = 0
    $dark = 0
    for ($ix = -5; $ix -le 5; $ix++) {
        for ($iy = -5; $iy -le 5; $iy++) {
            for ($iz = -5; $iz -le 5; $iz++) {
                $cx = $center + $ix * $step
                $cy = $center + $iy * $step
                $cz = $center + $iz * $step
                $dx = $cx - $center
                $dy = $cy - $center
                $dz = $cz - $center
                $r = [Math]::Sqrt($dx * $dx + $dy * $dy + $dz * $dz)
                if ($r -le $radius -and $r -gt $inner) {
                    $nx = $dx / $r
                    $ny = $dy / $r
                    $nz = $dz / $r
                    $best = -1.0
                    for ($j = 0; $j -lt $normals.Count; $j += 3) {
                        $dot = $nx * $normals[$j] + $ny * $normals[$j + 1] + $nz * $normals[$j + 2]
                        if ($dot -gt $best) { $best = $dot }
                    }
                    # 深色五边形 + 钨色六边形（不再用大块金色格，避免"纯色块"观感）
                    $tex = Choose ($best -gt 0.940) 'dark' 'tung'
                    if ($tex -eq 'dark') { $dark++ }
                    # 正弦哈希：给每一格一个 0~1 的贴图偏移，避免所有格取到同一小块像素
                    $hash = [Math]::Sin($ix * 12.9898 + $iy * 78.233 + $iz * 37.719) * 43758.5453
                    $hash = $hash - [Math]::Floor($hash)
                    Add-Box $l ($cx - $half) ($cy - $half) ($cz - $half) ($cx + $half) ($cy + $half) ($cz + $half) $tex $hash
                    $count++
                }
            }
        }
    }
    Write-Host "shell voxels: $count (pentagon $dark)"
}

# 6 个方向的重型夹持板：下界合金法兰 + 黑色石面板（插栓由载体方块单独渲染）
function Add-Plates($l) {
    foreach ($f in $script:plateFaces) {
        $n = $f[0]; $u = $f[1]; $v = $f[2]
        Add-PoleBox $l $n $u $v 0.0 0.0 6.1 6.1 12.70 13.30 'neth'
        Add-PoleBox $l $n $u $v 0.0 0.0 5.05 5.05 13.30 13.70 'dark'
    }
}

# 框架 8 个内角 → 球壳的斜撑：JSON 只支持轴对齐长方体与单轴 45°，
# 这里用 6 段阶梯块拼出对角线支撑，把"悬浮"的球和框架连起来。
function Add-Struts($l) {
    $center = 16.0
    $corner = 13.5
    $reach  = 13.4
    foreach ($sx in -1, 1) {
        foreach ($sy in -1, 1) {
            foreach ($sz in -1, 1) {
                $px = $center + $sx * $corner
                $py = $center + $sy * $corner
                $pz = $center + $sz * $corner
                $ex = $center + $sx * $reach / [Math]::Sqrt(3.0)
                $ey = $center + $sy * $reach / [Math]::Sqrt(3.0)
                $ez = $center + $sz * $reach / [Math]::Sqrt(3.0)
                for ($k = 0; $k -lt 6; $k++) {
                    $t = ($k + 0.5) / 6.0
                    $bx = $px + ($ex - $px) * $t
                    $by = $py + ($ey - $py) * $t
                    $bz = $pz + ($ez - $pz) * $t
                    $tex = Choose ($k -eq 5) 'copper' 'neth'
                    Add-Box $l ($bx - 0.95) ($by - 0.95) ($bz - 0.95) ($bx + 0.95) ($by + 0.95) ($bz + 0.95) $tex
                }
            }
        }
    }
    Write-Host "corner struts: 8"
}

# --------------------------------------------------------------------------- 输出
# 框架直接复用裂变线机器的 12 根主梁 + 8 个垫脚，几何与 UV 完全一致
$fission = Get-Content -Raw -LiteralPath (Join-Path $assets 'models\block\ore_leacher.json') | ConvertFrom-Json
$frame = @(@($fission.elements)[0..11]) + @(@($fission.elements)[39..46])

$bolts = New-BoltSpecs

$body = New-Object System.Collections.ArrayList
foreach ($e in $frame) { [void]$body.Add($e) }
Add-Shell $body
Add-Plates $body
Add-Struts $body
Save-Model 'muon_fusion' $body

# 物品模型：球是固定的，插栓按静止位置画上去（方块里的插栓是单独动画的载体）
$item = New-Object System.Collections.ArrayList
foreach ($e in $body) { [void]$item.Add($e) }
foreach ($b in $bolts) { Add-BoltPiston $item $b }
Save-Model 'muon_fusion_item' $item

# 每颗插栓一个隐藏载体方块（无物品 / 不进创造页），动画在 FissionRenderer 里做
foreach ($b in $bolts) {
    $carrier = New-Object System.Collections.ArrayList
    Add-BoltPiston $carrier $b
    Save-Model $b.name $carrier
    Save-Text (Join-Path $assets "blockstates\$($b.name).json") ("{`n    `"variants`": {`n        `"`": { `"model`": `"stellar_evolution:block/$($b.name)`" }`n    }`n}`n")
}

# 方块状态：主模型 + 侧面 8 个铜垫 + 底面 4 个铜垫，共 12 个
Save-Text (Join-Path $assets 'blockstates\muon_fusion.json') @'
{
    "multipart": [
        { "apply": { "model": "stellar_evolution:block/muon_fusion" } },
        { "when": { "port_north_a": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_north_a_open" } },
        { "when": { "port_north_a": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_north_a_capped" } },
        { "when": { "port_north_b": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_north_b_open" } },
        { "when": { "port_north_b": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_north_b_capped" } },
        { "when": { "port_east_a": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_east_a_open" } },
        { "when": { "port_east_a": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_east_a_capped" } },
        { "when": { "port_east_b": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_east_b_open" } },
        { "when": { "port_east_b": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_east_b_capped" } },
        { "when": { "port_south_a": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_south_a_open" } },
        { "when": { "port_south_a": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_south_a_capped" } },
        { "when": { "port_south_b": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_south_b_open" } },
        { "when": { "port_south_b": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_south_b_capped" } },
        { "when": { "port_west_a": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_west_a_open" } },
        { "when": { "port_west_a": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_west_a_capped" } },
        { "when": { "port_west_b": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_west_b_open" } },
        { "when": { "port_west_b": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_west_b_capped" } },
        { "when": { "port_down_a": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_a_open" } },
        { "when": { "port_down_a": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_a_capped" } },
        { "when": { "port_down_b": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_b_open" } },
        { "when": { "port_down_b": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_b_capped" } },
        { "when": { "port_down_c": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_c_open" } },
        { "when": { "port_down_c": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_c_capped" } },
        { "when": { "port_down_d": "false" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_d_open" } },
        { "when": { "port_down_d": "true" }, "apply": { "model": "stellar_evolution:block/ore_leacher_port_down_d_capped" } }
    ]
}
'@
Save-Text (Join-Path $assets 'blockstates\muon_fusion_shell.json') @'
{
    "variants": {
        "": { "model": "stellar_evolution:block/fission_machine_shell" }
    }
}
'@
Save-Text (Join-Path $assets 'items\muon_fusion.json') @'
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:block/muon_fusion_item"
    }
}
'@
Save-Text (Join-Path $data 'loot_table\blocks\muon_fusion.json') @'
{
    "type": "minecraft:block",
    "pools": [
        {
            "rolls": 1,
            "entries": [
                {
                    "type": "minecraft:item",
                    "name": "stellar_evolution:muon_fusion"
                }
            ],
            "conditions": [
                {
                    "condition": "minecraft:survives_explosion"
                }
            ]
        }
    ],
    "random_sequence": "stellar_evolution:blocks/muon_fusion"
}
'@

Write-Host 'done.'

# gen_large_copper_pipe.ps1 —— 6×6 管族（方块 id = machine_pipe / pressure_pipe）：照铜管口径，但更粗。
#
# 断面（2px 格对齐，中心在 8）：
#   · 机械管：外径 6×6（5..11）、壁厚 2px、中心 2×2 空心（7..9）；
#     外表面「切制铜块」、空心内壁 / 中心空腔面「磨制黑石」。
#   · 压力管：**实心** 6×6（不再空心），中心 2×2 那一条用「深板岩瓦」、其余用「下界合金块」。
#   · 每种管出 3 个模型：core（机械管 = 12 条棱 → 20 个 2×2×2 实心格，6 面各留 2×2 开口；
#                          压力管 = 3×3×3 全实心，中心十字 7 格换深板岩瓦）
#                      arm （沿 -Z 的管臂：从方块面往外 3 层 × 9 格；机械管中间 2×2 贯通，压力管实心、中间 2×2 深板岩瓦）
#                      straight（物品图标：整根直管）
#   面名 与 Java 端 CONNECTIONS（原版 6 个朝向属性）一致，两处必须相同。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_large_copper_pipe.ps1
$ErrorActionPreference = 'Stop'

$root     = Split-Path -Parent $PSScriptRoot
$assets   = Join-Path $root 'src\main\resources\assets\stellar_evolution'
$lootRoot = Join-Path $root 'src\main\resources\data\stellar_evolution\loot_table\blocks'

$UTF8    = New-Object System.Text.UTF8Encoding($false)
$ALIAS_OUT = '#outer'
$ALIAS_IN  = '#inner'
$ALIAS_CORE = '#core'
$FACES   = 'down', 'up', 'north', 'south', 'west', 'east'

# 2px 格的三个起点（中格 = 7，即中心那一格）
$STARTS = @(5, 7, 9)

function New-Face($tex, $cull) {
    $o = [pscustomobject]@{ texture = $tex }
    if ($cull) { $o | Add-Member -MemberType NoteProperty -Name cullface -Value $cull }
    return $o
}

# 造一个 2×2×2 的格。贴图优先级：$coreFaces（中心材质）> $innerFaces（内壁材质）> 外壁材质。
function New-Cell($x0, $y0, $z0, $innerFaces, $cullFace, $coreFaces) {
    # 注意：局部变量不能叫 $faces —— PowerShell 变量不区分大小写，会覆盖脚本级的 $FACES
    $faceObj = New-Object System.Management.Automation.PSObject
    foreach ($f in $FACES) {
        $tex = if ($coreFaces -contains $f) { $ALIAS_CORE }
               elseif ($innerFaces -contains $f) { $ALIAS_IN }
               else { $ALIAS_OUT }
        $cull = if ($cullFace -eq $f) { $f } else { $null }
        $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value (New-Face $tex $cull)
    }
    return [pscustomobject]@{ from = @($x0, $y0, $z0); to = @(($x0 + 2), ($y0 + 2), ($z0 + 2)); faces = $faceObj }
}

# 逐元素「微偏移」（向内 k*0.00137，<0.07px）：消除相邻格之间精确共面的面（同向共面 = z-fighting）。
function Write-Model($file, $elements) {
    $out = @()
    $k = 0
    foreach ($e in $elements) {
        $d = -($k * 0.00137)
        $nf = @(); $nt = @()
        for ($i = 0; $i -lt 3; $i++) { $nf += [double]$e.from[$i] + $d; $nt += [double]$e.to[$i] + $d }
        $out += [pscustomobject]@{ from = $nf; to = $nt; faces = $e.faces }
        $k++
    }
    $textures = [ordered]@{ outer = $script:TEX_OUT }
    if ($script:TEX_IN) { $textures.inner = $script:TEX_IN }
    if ($script:TEX_CORE) { $textures.core = $script:TEX_CORE }
    $textures.particle = $script:TEX_OUT
    $model = [pscustomobject]@{
        parent           = 'minecraft:block/block'
        ambientocclusion = $false
        textures         = [pscustomobject]$textures
        elements         = $out
    }
    [System.IO.File]::WriteAllText($file, ($model | ConvertTo-Json -Depth 12), $UTF8)
}

# 3×3 网格里，下标 1（x/z 起点 7）= 中格。
$MID = 1
$DIRS3 = @(
    @{ n = 'east';  dx = 1;  dy = 0;  dz = 0;  inner = 'east'  },   # 朝 +x
    @{ n = 'west';  dx = -1; dy = 0;  dz = 0;  inner = 'west'  },
    @{ n = 'up';    dx = 0;  dy = 1;  dz = 0;  inner = 'up'    },
    @{ n = 'down';  dx = 0;  dy = -1; dz = 0;  inner = 'down'  },
    @{ n = 'south'; dx = 0;  dy = 0;  dz = 1;  inner = 'south' },
    @{ n = 'north'; dx = 0;  dy = 0;  dz = -1; inner = 'north' }
)

# 通用：3×3×3 的 2px 格网格 → 元素列表。
# $sx/$sy/$sz = 三个轴各自的格起点数组（一般用 $STARTS；管臂的 -Z 轴要用 0/2/4 从方块面往外长）；
# $isVoid = 判定某格是不是「空」（不进模型）；$cullAt = 判定某个面要不要写 cullface（如管臂外端）；
# $coreAt = 判定某格是不是「中心材质」（返回面名数组，空数组 = 不是）。
function Build-Grid($sx, $sy, $sz, $isVoid, $cullAt, $coreAt) {
    $els = @()
    for ($xi = 0; $xi -lt 3; $xi++) {
        for ($yi = 0; $yi -lt 3; $yi++) {
            for ($zi = 0; $zi -lt 3; $zi++) {
                if (& $isVoid $xi $yi $zi) { continue }
                $dirs = @()
                foreach ($d in $DIRS3) {
                    $nx = $xi + $d.dx; $ny = $yi + $d.dy; $nz = $zi + $d.dz
                    if ($nx -lt 0 -or $nx -gt 2 -or $ny -lt 0 -or $ny -gt 2 -or $nz -lt 0 -or $nz -gt 2) { continue }
                    if (& $isVoid $nx $ny $nz) { $dirs += $d.inner }   # 邻居是空 → 这一面就是内壁
                }
                $cull = $null
                if ($cullAt) { $cull = & $cullAt $xi $yi $zi }
                $coreFaces = @()
                if ($coreAt) { $coreFaces = & $coreAt $xi $yi $zi }
                $els += New-Cell $sx[$xi] $sy[$yi] $sz[$zi] $dirs $cull $coreFaces
            }
        }
    }
    return $els
}

# 生成一种管：$id = 方块 id，$texOut = 外壁材质，$texIn = 内壁材质（空心管用），
# $coreTex = 中心材质（给了就是「实心 + 中心换材质」，不给就是原来的空心管）。
function Generate-Pipe($id, $texOut, $texIn, $coreTex) {
    $script:TEX_OUT  = $texOut
    $script:TEX_IN   = $texIn
    $script:TEX_CORE = $coreTex

    $modelsDir = Join-Path $assets "models\block\$id"
    $statesDir = Join-Path $assets 'blockstates'
    $itemsDir  = Join-Path $assets 'items'
    New-Item -ItemType Directory -Force -Path $modelsDir, $statesDir, $itemsDir, $lootRoot | Out-Null

    # 3×3 网格里「中格数」= 该格有几个轴落在中心层。
    $midCount = { param($x, $y, $z)
        $mids = 0
        if ($x -eq $MID) { $mids++ }
        if ($y -eq $MID) { $mids++ }
        if ($z -eq $MID) { $mids++ }
        return $mids
    }

    if ($coreTex) {
        # ── 实心 + 中心材质（压力管）──
        # core：3×3×3 全实心；中格数 ≥ 2 的那 7 格（6 面心 + 正中心）= 中心十字 → 用中心材质。
        $coreVoid = { param($x, $y, $z) return $false }
        $coreAt = { param($x, $y, $z)
            if ((& $midCount $x $y $z) -ge 2) { return $FACES }
            return @()
        }
        $core = Build-Grid $STARTS $STARTS $STARTS $coreVoid $null $coreAt

        # arm：沿 -Z 的 3 层 × 3×3 全实心；x/y 都在中心那一列（2×2 贯通条）= 中心材质。
        $armVoid = { param($x, $y, $z) return $false }
        $armAt = { param($x, $y, $z)
            if (($x -eq $MID) -and ($y -eq $MID)) { return $FACES }
            return @()
        }
        $armCull = { param($x, $y, $z)
            if ($z -eq 0) { return 'north' }
            return $null
        }
        $arm = Build-Grid $STARTS $STARTS @(0, 2, 4) $armVoid $armCull $armAt
    } else {
        # ── 空心管（机械管）──
        # core：12 条棱 → 实心 = 中格数为 0（角，8 个）或 1（棱中点，12 个）= 20 格；
        #       空 = 中格数 2（6 个面心）或 3（正中心）—— 即「6 面各留一个 2×2 开口 + 中心空腔」。
        $coreVoid = { param($x, $y, $z) return ((& $midCount $x $y $z) -ge 2) }
        $core = Build-Grid $STARTS $STARTS $STARTS $coreVoid $null $null

        # arm：沿 -Z 的管臂（从方块面 z0 往外算 3 层；中格在 x/y 都居中 → 2×2 贯通孔）；
        #      外端（zi=0，即 z0..2 那一层）写 cullface north。
        # 注意：z 轴起点必须是 0/2/4 —— 用 $STARTS 的话管臂会整段埋进中心框内部（碰撞有、模型看不见）。
        $armVoid = { param($x, $y, $z) return ($x -eq $MID) -and ($y -eq $MID) }
        $armCull = { param($x, $y, $z)
            if ($z -eq 0) { return 'north' }
            return $null
        }
        $arm = Build-Grid $STARTS $STARTS @(0, 2, 4) $armVoid $armCull $null
    }

    Write-Model (Join-Path $modelsDir 'core.json') $core
    Write-Model (Join-Path $modelsDir 'arm.json') $arm

    # ── straight：物品图标用的整根直管 ──
    $straight = @()
    # 4 片长壁：上/下壁横跨 x5..11，西/东壁只占 x5..7 / x9..11（中间留 2×2 中心区）
    $wallSpecs = @(
        @{ from = @(5, 9, 0); to = @(11, 11, 16) },   # 上壁
        @{ from = @(5, 5, 0); to = @(11, 7, 16) },    # 下壁
        @{ from = @(5, 7, 0); to = @(7, 9, 16) },     # 西壁
        @{ from = @(9, 7, 0); to = @(11, 9, 16) }     # 东壁
    )
    foreach ($w in $wallSpecs) {
        $faceObj = New-Object System.Management.Automation.PSObject
        foreach ($f in $FACES) {
            $cull = @{ north = 'north'; south = 'south' }[$f]
            $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value (New-Face $ALIAS_OUT $cull)
        }
        $straight += [pscustomobject]@{ from = $w.from; to = $w.to; faces = $faceObj }
    }
    # 中心 2×2 长条：空心管留空、实心管填中心材质（两端面写 cullface，微偏移避免与壁共面）。
    if ($coreTex) {
        $faceObj = New-Object System.Management.Automation.PSObject
        foreach ($f in $FACES) {
            $cull = @{ north = 'north'; south = 'south' }[$f]
            $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value (New-Face $ALIAS_CORE $cull)
        }
        $straight += [pscustomobject]@{ from = @(7, 7, 0); to = @(9, 9, 16); faces = $faceObj }
    }
    Write-Model (Join-Path $modelsDir 'straight.json') $straight

    # ── blockstate：core 常驻 + 六个方向各按「该向是否连接」叠一个 arm（arm 画在 -Z，其余方向旋转复用） ──
    $parts = New-Object System.Collections.Generic.List[object]
    $parts.Add([pscustomobject]@{ apply = [pscustomobject]@{ model = "stellar_evolution:block/$id/core" } })
    $armSpecs = @(
        @{ when = 'north'; rot = @{} },
        @{ when = 'east';  rot = @{ y = 90 } },
        @{ when = 'south'; rot = @{ y = 180 } },
        @{ when = 'west';  rot = @{ y = 270 } },
        @{ when = 'up';    rot = @{ x = 270 } },
        @{ when = 'down';  rot = @{ x = 90 } }
    )
    foreach ($a in $armSpecs) {
        $when = New-Object System.Management.Automation.PSObject
        $when | Add-Member -MemberType NoteProperty -Name $a.when -Value 'true'
        $apply = New-Object System.Management.Automation.PSObject
        $apply | Add-Member -MemberType NoteProperty -Name 'model' -Value "stellar_evolution:block/$id/arm"
        $apply | Add-Member -MemberType NoteProperty -Name 'uvlock' -Value $true
        foreach ($k in $a.rot.Keys) { $apply | Add-Member -MemberType NoteProperty -Name $k -Value $a.rot[$k] }
        $parts.Add([pscustomobject]@{ when = $when; apply = $apply })
    }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "$id.json"),
        ([pscustomobject]@{ multipart = $parts.ToArray() } | ConvertTo-Json -Depth 10), $UTF8)

    # ── 物品模型 + 掉落表 ──
    $item = [pscustomobject]@{ model = [pscustomobject]@{ type = 'minecraft:model'; model = "stellar_evolution:block/$id/straight" } }
    [System.IO.File]::WriteAllText((Join-Path $itemsDir "$id.json"), ($item | ConvertTo-Json -Depth 6), $UTF8)

    $loot = [pscustomobject]@{
        type   = 'minecraft:block'
        pools  = @([pscustomobject]@{
            rolls      = 1
            entries    = @([pscustomobject]@{ type = 'minecraft:item'; name = "stellar_evolution:$id" })
            conditions = @([pscustomobject]@{ condition = 'minecraft:survives_explosion' })
        })
        random_sequence = "stellar_evolution:blocks/$id"
    }
    [System.IO.File]::WriteAllText((Join-Path $lootRoot "$id.json"), ($loot | ConvertTo-Json -Depth 10), $UTF8)

    Write-Host ("完成 {0}：core {1} 格 / arm {2} 格 / straight {3} 片 + blockstate + 物品 + 掉落" -f `
        $id, $core.Count, $arm.Count, $straight.Count)
}

# 机械管：切制铜块外皮 + 磨制黑石内壁 + 中心 2×2 空心（既有资产，重跑内容不变）。
Generate-Pipe 'machine_pipe' 'minecraft:block/cut_copper' 'minecraft:block/polished_blackstone' $null
# 压力管：实心，下界合金块外皮 + 中心 2×2 深板岩瓦。
Generate-Pipe 'pressure_pipe' 'minecraft:block/netherite_block' $null 'minecraft:block/deepslate_tiles'
# 束流管：实心，钨块外皮 + 中心 2×2 磨制安山岩（粒子流网络载体）。
Generate-Pipe 'beam_pipe' 'stellar_evolution:block/tungsten_block' $null 'minecraft:block/polished_andesite'

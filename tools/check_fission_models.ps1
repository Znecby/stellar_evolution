# check_fission_models.ps1 —— 裂变机器模型自检（照石化线 check_petro_models.ps1 口径）。
#
#   · 共面闪烁（fight）/ 零体积（zero）/ 重复（dup）/ 越界（bound）
#   · 浮空（float）：机体元素是否都与框架 / 机体连成一体
#   · 扫掠穿模（sweep）：可动部件绕枢轴扫掠是否撞到静止件
#   · 部件悬挂（hang）：可动部件是否贴在机体或其他部件上
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/check_fission_models.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Web.Extensions
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$root = Split-Path -Parent $PSScriptRoot
$dir  = Join-Path $root 'src\main\resources\assets\stellar_evolution\models\block'

$bodies = 'ore_leacher', 'fission_centrifuge', 'canning_machine', 'water_reactor', 'steam_turbine_unit',
          'cyclotron', 'particle_source', 'irradiation_chamber', 'collider', 'ads', 'annihilation_reactor'
$partsByMachine = @{
    ore_leacher        = @('ore_leacher_agitator')
    fission_centrifuge = @('gas_centrifuge_rotor_a', 'gas_centrifuge_rotor_b', 'gas_centrifuge_fan')
    canning_machine    = @('fuel_assembler_flywheel', 'fuel_assembler_dial')
    water_reactor      = @('water_reactor_crd', 'water_reactor_pump', 'water_reactor_gauge')
    steam_turbine_unit = @('steam_turbine_rotor', 'steam_turbine_governor', 'steam_turbine_gauge')
    particle_source    = @('particle_source_turbofan', 'particle_source_wheel', 'particle_source_cooler',
                           'particle_source_cooler_w', 'particle_source_gasvalve')
    irradiation_chamber = @('irradiation_chamber_carousel', 'irradiation_chamber_fan', 'irradiation_chamber_gauge',
                            'irradiation_chamber_shutter', 'irradiation_chamber_scanner')
    collider           = @('collider_beam_a', 'collider_beam_b', 'collider_cryofan', 'collider_gauge',
                           'collider_shutter_n', 'collider_shutter_s')
    # 回旋加速器复用离心机风扇载体，枢轴不同 → 用 $pivotsByMachine 覆盖
    cyclotron          = @('gas_centrifuge_fan', 'cyclotron_deflector')
    # ADS / 湮灭反应堆：三期 E5 换专属外观后均为静态机体（无隐藏载体部件）
    ads                = @()
    annihilation_reactor = @()
}
# 与 Java 端 FissionMachineStructure 的 FissionMachinePart（轴 + 枢轴）一一对应，两处必须一致
$axes = @{
    ore_leacher_agitator='Y'
    gas_centrifuge_rotor_a='Y'; gas_centrifuge_rotor_b='Y'; gas_centrifuge_fan='Y'
    fuel_assembler_flywheel='Y'; fuel_assembler_dial='Z'
    water_reactor_crd='Y';    water_reactor_pump='Y';    water_reactor_gauge='Z'
    steam_turbine_rotor='X';  steam_turbine_governor='Y'; steam_turbine_gauge='Z'
    particle_source_turbofan='Y'; particle_source_wheel='Z'; particle_source_cooler='X'
    particle_source_cooler_w='X'; particle_source_gasvalve='Z'
    irradiation_chamber_carousel='Y'; irradiation_chamber_fan='Y'; irradiation_chamber_gauge='Z'
    irradiation_chamber_shutter='Z'; irradiation_chamber_scanner='X'
    collider_beam_a='Z'; collider_beam_b='Z'; collider_cryofan='Y'; collider_gauge='Z'
    collider_shutter_n='Z'; collider_shutter_s='Z'
    cyclotron_deflector='Z'
}
$pivots = @{
    ore_leacher_agitator=@(16,16,16)
    gas_centrifuge_rotor_a=@(10,16,10); gas_centrifuge_rotor_b=@(22,16,10); gas_centrifuge_fan=@(16,16,16)
    fuel_assembler_flywheel=@(16,16,16); fuel_assembler_dial=@(8,16,3.35)
    water_reactor_crd=@(16,16,16);    water_reactor_pump=@(25.6,10,25.6); water_reactor_gauge=@(8,16,3.35)
    steam_turbine_rotor=@(16,16,16);  steam_turbine_governor=@(24,26,16); steam_turbine_gauge=@(8,16,3.35)
    particle_source_turbofan=@(16,23.9,20.9); particle_source_wheel=@(16,13,30.2); particle_source_cooler=@(21.2,13,24.6)
    particle_source_cooler_w=@(10.4,13,26); particle_source_gasvalve=@(16,12,0.4)
    irradiation_chamber_carousel=@(16,22.4,16); irradiation_chamber_fan=@(22.8,27.4,22.8)
    irradiation_chamber_gauge=@(8.8,15.8,4.63)
    irradiation_chamber_shutter=@(16,12.9,30.3); irradiation_chamber_scanner=@(27,13,10.5)
    collider_beam_a=@(16,13.5,10.2); collider_beam_b=@(16,13.5,22.0)
    collider_cryofan=@(16,23.2,16); collider_gauge=@(8.7,11.6,3.48)
    collider_shutter_n=@(16,13.5,1.0); collider_shutter_s=@(16,13.5,30.4)
    cyclotron_deflector=@(8.5,15.35,2.28)
}
# 同一部件被多台机器复用时，按「机器 → 部件 → 枢轴」覆盖全局 $pivots
$pivotsByMachine = @{
    cyclotron = @{ gas_centrifuge_fan = @(16,27,16) }
}
$eps = 0.0001
$tol = 0.09

function Load-Elems($id) {
    $path = Join-Path $dir "$id.json"
    $list = New-Object System.Collections.ArrayList
    if (-not (Test-Path $path)) { Write-Host "MISSING $id"; return $list }
    $o = $ser.DeserializeObject([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8))
    foreach ($e in $o['elements']) {
        $f = $e['from']; $t = $e['to']
        [void]$list.Add([pscustomobject]@{
            fn = @([double]$f[0], [double]$f[1], [double]$f[2])
            tn = @([double]$t[0], [double]$t[1], [double]$t[2]) })
    }
    return $list
}
function Ov($a1, $a0, $b1, $b0) { return [Math]::Min($a1, $b1) - [Math]::Max($a0, $b0) }
function Touch($a, $b) {
    return ((Ov $a.tn[0] $a.fn[0] $b.tn[0] $b.fn[0]) -ge -$tol) -and
           ((Ov $a.tn[1] $a.fn[1] $b.tn[1] $b.fn[1]) -ge -$tol) -and
           ((Ov $a.tn[2] $a.fn[2] $b.tn[2] $b.fn[2]) -ge -$tol)
}

$fight = 0; $zero = 0; $dup = 0; $bound = 0; $floatCnt = 0; $frameDetached = 0; $sweep = 0; $hang = 0

foreach ($id in ($bodies + ($partsByMachine.Values | ForEach-Object { $_ }))) {
    $els = @(Load-Elems $id)
    for ($i = 0; $i -lt $els.Count; $i++) {
        $a = $els[$i]
        if (($a.tn[0] - $a.fn[0]) -lt $eps -or ($a.tn[1] - $a.fn[1]) -lt $eps -or ($a.tn[2] - $a.fn[2]) -lt $eps) {
            Write-Host ("ZERO  {0} #{1}" -f $id, $i); $zero++
        }
        if ($a.tn[0] -gt 32 -or $a.tn[1] -gt 32 -or $a.tn[2] -gt 32 -or
            $a.fn[0] -lt -16 -or $a.fn[1] -lt -16 -or $a.fn[2] -lt -16) {
            Write-Host ("BOUND {0} #{1}" -f $id, $i); $bound++
        }
        for ($j = $i + 1; $j -lt $els.Count; $j++) {
            $b = $els[$j]
            if ($a.fn[0] -eq $b.fn[0] -and $a.fn[1] -eq $b.fn[1] -and $a.fn[2] -eq $b.fn[2] -and
                $a.tn[0] -eq $b.tn[0] -and $a.tn[1] -eq $b.tn[1] -and $a.tn[2] -eq $b.tn[2]) {
                Write-Host ("DUP   {0} #{1}=#{2}" -f $id, $i, $j); $dup++; continue
            }
            for ($ax = 0; $ax -lt 3; $ax++) {
                $o1 = ($ax + 1) % 3; $o2 = ($ax + 2) % 3
                if ((Ov $a.tn[$o1] $a.fn[$o1] $b.tn[$o1] $b.fn[$o1]) -le 0.01) { continue }
                if ((Ov $a.tn[$o2] $a.fn[$o2] $b.tn[$o2] $b.fn[$o2]) -le 0.01) { continue }
                $same = ([Math]::Abs($a.fn[$ax] - $b.fn[$ax]) -lt $eps) -or ([Math]::Abs($a.tn[$ax] - $b.tn[$ax]) -lt $eps)
                if ($same) { Write-Host ("FIGHT {0} #{1} #{2} axis={3}" -f $id, $i, $j, $ax); $fight++ }
            }
        }
    }
}

foreach ($m in $bodies) {
    $bel = @(Load-Elems $m)
    $n = $bel.Count
    $start = 12   # 前 12 个是框架（悬在机体外）→ 只检查机体元素与整体连通
    $visited = New-Object bool[] $n
    $q = New-Object System.Collections.Queue
    if ($n -gt $start) { $visited[$start] = $true; $q.Enqueue($start) }
    while ($q.Count -gt 0) {
        $u = [int]$q.Dequeue()
        for ($j = $start; $j -lt $n; $j++) {
            if ((-not $visited[$j]) -and (Touch $bel[$u] $bel[$j])) { $visited[$j] = $true; $q.Enqueue($j) }
        }
    }
    for ($i = $start; $i -lt $n; $i++) {
        if (-not $visited[$i]) { Write-Host ("FLOAT {0} #{1}" -f $m, $i); $floatCnt++ }
    }
    $allVisited = New-Object bool[] $n
    $q2 = New-Object System.Collections.Queue
    if ($n -gt 0) { $allVisited[0] = $true; $q2.Enqueue(0) }
    while ($q2.Count -gt 0) {
        $u3 = [int]$q2.Dequeue()
        for ($j = 0; $j -lt $n; $j++) {
            if ((-not $allVisited[$j]) -and (Touch $bel[$u3] $bel[$j])) { $allVisited[$j] = $true; $q2.Enqueue($j) }
        }
    }
    $detached = 0
    for ($i = 0; $i -lt $n; $i++) { if (-not $allVisited[$i]) { $detached++ } }
    if ($detached -gt 0) { Write-Host ("FRAME {0}: {1} 个元素与下界合金框架不连通" -f $m, $detached); $frameDetached++ }

    foreach ($partId in $partsByMachine[$m]) {
        $pel = @(Load-Elems $partId)
        $ax = $axes[$partId]
        $p = if ($pivotsByMachine.ContainsKey($m) -and $pivotsByMachine[$m].ContainsKey($partId)) {
            $pivotsByMachine[$m][$partId]
        } else {
            $pivots[$partId]
        }
        $ai = switch ($ax) { 'X' { 0 } 'Y' { 1 } 'Z' { 2 } }
        $u2 = ($ai + 1) % 3; $v2 = ($ai + 2) % 3
        foreach ($pe in $pel) {
            $amin = $pe.fn[$ai]; $amax = $pe.tn[$ai]; $r = 0.0
            foreach ($cf in @($pe.fn[$u2], $pe.tn[$u2])) {
                foreach ($cg in @($pe.fn[$v2], $pe.tn[$v2])) {
                    $d0 = [Math]::Sqrt([Math]::Pow([double]$cf - $p[$u2], 2) + [Math]::Pow([double]$cg - $p[$v2], 2))
                    if ($d0 -gt $r) { $r = $d0 }
                }
            }
            for ($bi = 0; $bi -lt $n; $bi++) {
                $b = $bel[$bi]
                if ((Ov $amax $amin $b.tn[$ai] $b.fn[$ai]) -le 0.01) { continue }
                $du = 0.0; $dv = 0.0
                if ($p[$u2] -gt $b.tn[$u2]) { $du = $p[$u2] - $b.tn[$u2] } elseif ($p[$u2] -lt $b.fn[$u2]) { $du = $b.fn[$u2] - $p[$u2] }
                if ($p[$v2] -gt $b.tn[$v2]) { $dv = $p[$v2] - $b.tn[$v2] } elseif ($p[$v2] -lt $b.fn[$v2]) { $dv = $b.fn[$v2] - $p[$v2] }
                $d = [Math]::Sqrt($du * $du + $dv * $dv)
                if ($d -le $r + 0.05) {
                    Write-Host ("SWEEP {0} {1} 元素({2},{3},{4})-({5},{6},{7}) r={8:N2} vs 机#{9} 距轴 {10:N2}" -f `
                        $m, $partId, $pe.fn[0], $pe.fn[1], $pe.fn[2], $pe.tn[0], $pe.tn[1], $pe.tn[2], $r, $bi, $d)
                    $sweep++
                }
            }
        }
    }
    # 部件悬挂（hang）：可动部件必须贴住机体或另一个部件
    foreach ($partId in $partsByMachine[$m]) {
        $pel = @(Load-Elems $partId)
        $best = 999.0
        foreach ($pe in $pel) {
            $others = New-Object System.Collections.ArrayList
            foreach ($o in $bel) { [void]$others.Add($o) }
            foreach ($otherId in $partsByMachine[$m]) {
                if ($otherId -eq $partId) { continue }
                foreach ($o in @(Load-Elems $otherId)) { [void]$others.Add($o) }
            }
            foreach ($o in $others) {
                $s = 0.0
                for ($k = 0; $k -lt 3; $k++) {
                    $g = [Math]::Max([Math]::Max($pe.fn[$k] - $o.tn[$k], $o.fn[$k] - $pe.tn[$k]), 0)
                    $s += $g * $g
                }
                $d = [Math]::Sqrt($s)
                if ($d -lt $best) { $best = $d }
            }
        }
        if ($best -gt 0.5) {
            Write-Host ("HANG {0} {1} 离机体/其他部件 {2:N2}（悬空）" -f $m, $partId, $best)
            $hang++
        }
    }
}

Write-Host ("==== fight={0}  zero={1}  dup={2}  bound={3}  float={4}  frame_detached={5}  sweep={6}  hang={7}" -f `
    $fight, $zero, $dup, $bound, $floatCnt, $frameDetached, $sweep, $hang)

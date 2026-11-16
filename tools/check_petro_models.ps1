# check_petro_models.ps1 —— 石化机器模型自检（照生化线 tools/check_coplanar.ps1 + check_float_sweep.ps1 口径）。
#
#   · 共面闪烁（fight）：同向共面且投影重叠 = 真 z-fighting
#   · 零体积（zero） / 重复（dup） / 越界（bound）
#   · 浮空（float）：机体元素是否都与铜框架/机体连成一体
#   · 扫掠穿模（sweep）：可动部件元素绕枢轴扫掠是否撞到静止件
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/check_petro_models.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Web.Extensions
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$root = Split-Path -Parent $PSScriptRoot
$dir  = Join-Path $root 'src\main\resources\assets\stellar_evolution\models\block'

$bodies = 'distillation_tower', 'cracking_furnace', 'reformer', 'polymerizer', 'gasifier', 'synthesis_tower'
$partsByMachine = @{
    distillation_tower = @('distillation_tower_fan', 'distillation_tower_valve', 'distillation_tower_gauge')
    cracking_furnace   = @('cracking_furnace_fan', 'cracking_furnace_handwheel', 'cracking_furnace_dial')
    reformer           = @('reformer_agitator', 'reformer_valve', 'reformer_valve_wheel')
    polymerizer        = @('polymerizer_agitator', 'polymerizer_coupling', 'polymerizer_drain_valve')
    gasifier           = @('gasifier_fan', 'gasifier_valve', 'gasifier_gauge')
    synthesis_tower    = @('synthesis_tower_agitator', 'synthesis_tower_valve', 'synthesis_tower_gauge')
}
# 与 Java 端 PetroMachineKind 的 PetroMachinePart（轴 + 枢轴）一一对应，两处必须一致
$axes = @{
    distillation_tower_fan='Y'; distillation_tower_valve='Y'; distillation_tower_gauge='Z'
    cracking_furnace_fan='Y';   cracking_furnace_handwheel='Z'; cracking_furnace_dial='Z'
    reformer_agitator='Y';      reformer_valve='Y';            reformer_valve_wheel='Z'
    polymerizer_agitator='Y';   polymerizer_coupling='Y';      polymerizer_drain_valve='Y'
    gasifier_fan='Y';           gasifier_valve='Y';             gasifier_gauge='Z'
    synthesis_tower_agitator='Y'; synthesis_tower_valve='Y';  synthesis_tower_gauge='Z'
}
$pivots = @{
    distillation_tower_fan=@(16,16,16); distillation_tower_valve=@(16,16,16); distillation_tower_gauge=@(16,16,3.35)
    cracking_furnace_fan=@(16,16,16);   cracking_furnace_handwheel=@(16,16,3.35); cracking_furnace_dial=@(8,16,3.35)
    reformer_agitator=@(16,16,16);      reformer_valve=@(16,16,16);            reformer_valve_wheel=@(16,16,3.35)
    polymerizer_agitator=@(16,16,16);   polymerizer_coupling=@(16,16,16);      polymerizer_drain_valve=@(16,16,16)
    gasifier_fan=@(16,16,16);          gasifier_valve=@(16,16,16);            gasifier_gauge=@(16,16,3.35)
    synthesis_tower_agitator=@(16,16,16); synthesis_tower_valve=@(16,16,16); synthesis_tower_gauge=@(16,16,3.35)
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
    $start = 12   # 前 12 个是铜框架（悬在机体外）→ 只检查机体元素与整体连通
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
    if ($detached -gt 0) { Write-Host ("FRAME {0}: {1} 个元素与铜框架不连通" -f $m, $detached); $frameDetached++ }

    foreach ($partId in $partsByMachine[$m]) {
        $pel = @(Load-Elems $partId)
        $ax = $axes[$partId]; $p = $pivots[$partId]
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
    # 部件悬挂（hang）：可动部件必须「挂」在机体或另一个部件上 —— 否则在游戏里就是悬空的贴图
    #（这类问题只查「连通性 / 扫掠」是查不出来的：部件是独立模型，不参与机体的浮空判定）
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

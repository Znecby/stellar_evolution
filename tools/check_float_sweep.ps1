Add-Type -AssemblyName System.Web.Extensions
$ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
$dir = "src\main\resources\assets\stellar_evolution\models\block"

$bodies = 'slurry_mixer','fermenter','decomposer','sterilizer','crystallizer','reactor'
$partsByMachine = @{
    slurry_mixer = @('slurry_mixer_agitator','slurry_mixer_anchor')
    fermenter    = @('fermenter_agitator','fermenter_coupling')
    decomposer   = @('decomposer_drum','decomposer_pulley')
    sterilizer   = @('sterilizer_handwheel','sterilizer_needle')
    crystallizer = @('crystallizer_crystal','crystallizer_valve')
    reactor      = @('bioreactor_agitator','bioreactor_sparger')
}
$axes = @{
    slurry_mixer_agitator='Y'; slurry_mixer_anchor='Y'; fermenter_agitator='Y'; fermenter_coupling='Y'
    decomposer_drum='X'; decomposer_pulley='X'; sterilizer_handwheel='Z'; sterilizer_needle='Y'
    crystallizer_crystal='Y'; crystallizer_valve='Y'; bioreactor_agitator='Y'; bioreactor_sparger='Y'
}
$pivots = @{
    slurry_mixer_agitator=@(16,16,16); slurry_mixer_anchor=@(16,16,16)
    fermenter_agitator=@(16,16,16);    fermenter_coupling=@(16,16,16)
    decomposer_drum=@(16,18,16);       decomposer_pulley=@(16,18,16)
    sterilizer_handwheel=@(16,16,3.35); sterilizer_needle=@(13.7,29.5,14.0)
    crystallizer_crystal=@(16,16,16);  crystallizer_valve=@(16,16,16)
    bioreactor_agitator=@(16,16,16);        bioreactor_sparger=@(16,16,16)
}
$tol = 0.09

function Load-Elems($id) {
    $path = Join-Path $dir "$id.json"
    $list = New-Object System.Collections.ArrayList
    if (-not (Test-Path $path)) { Write-Host "MISSING $id"; return $list }
    $o = $ser.DeserializeObject([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8))
    foreach ($e in $o['elements']) {
        $f = $e['from']; $t = $e['to']
        [void]$list.Add([pscustomobject]@{ fn = @([double]$f[0],[double]$f[1],[double]$f[2]); tn = @([double]$t[0],[double]$t[1],[double]$t[2]) })
    }
    return $list
}
function Ov($a1, $a0, $b1, $b0) { return [Math]::Min($a1, $b1) - [Math]::Max($a0, $b0) }
function Touch($a, $b) {
    return ((Ov $a.tn[0] $a.fn[0] $b.tn[0] $b.fn[0]) -ge -$tol) -and ((Ov $a.tn[1] $a.fn[1] $b.tn[1] $b.fn[1]) -ge -$tol) -and ((Ov $a.tn[2] $a.fn[2] $b.tn[2] $b.fn[2]) -ge -$tol)
}

$floatCnt = 0; $sweep = 0; $frameDetached = 0
foreach ($m in $bodies) {
    $bel = @(Load-Elems $m)
    $n = $bel.Count
    $start = 12   # 前 12 个是铜框架，本就悬在机体外 → 不参与（也不作为浮空判据）
    # ① 浮空：从第 12 个元素 BFS，凡不连通的机体元素 = 浮空
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
        if (-not $visited[$i]) {
            $a = $bel[$i]
            Write-Host ("FLOAT {0} #{1}  ({2},{3},{4})-({5},{6},{7})" -f $m, $i, $a.fn[0], $a.fn[1], $a.fn[2], $a.tn[0], $a.tn[1], $a.tn[2]); $floatCnt++
        }
    }
    # ③ 机体是否与铜框架连成一体（整个模型应只有一个连通块；底部铜管连接件负责把两者连起来）
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
    # ② 扫掠穿模：逐【部件元素】的扫掠圆柱 vs 机体
    foreach ($partId in $partsByMachine[$m]) {
        $pel = @(Load-Elems $partId)
        $ax = $axes[$partId]; $p = $pivots[$partId]
        $ai = switch ($ax) { 'X' {0} 'Y' {1} 'Z' {2} }
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
                    Write-Host ("SWEEP {0} {1} 元素({2},{3},{4})-({5},{6},{7}) r={8:N2} vs 机#{9}({10},{11},{12})-({13},{14},{15}) 距轴 {16:N2}" -f $m, $partId, $pe.fn[0], $pe.fn[1], $pe.fn[2], $pe.tn[0], $pe.tn[1], $pe.tn[2], $r, $bi, $b.fn[0], $b.fn[1], $b.fn[2], $b.tn[0], $b.tn[1], $b.tn[2], $d); $sweep++
                }
            }
        }
    }
}
Write-Host ("==== float={0}  frame_detached={1}  sweep={2}" -f $floatCnt, $frameDetached, $sweep)

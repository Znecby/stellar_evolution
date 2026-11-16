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
$eps = 0.0001

function Load-Elems($id) {
    $path = Join-Path $dir "$id.json"
    if (-not (Test-Path $path)) { Write-Host "MISSING $id"; return @() }
    $o = $ser.DeserializeObject([System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8))
    $list = @()
    foreach ($e in $o['elements']) {
        $f = $e['from']; $t = $e['to']
        $list += [pscustomobject]@{ fx = [double]$f[0]; fy = [double]$f[1]; fz = [double]$f[2]; tx = [double]$t[0]; ty = [double]$t[1]; tz = [double]$t[2] }
    }
    return $list
}

# 同向共面 = 真 z-fighting（两面法线同向、同一平面且投影重叠）；背面相贴（A.to=B.from）无害
$fight = 0; $zero = 0; $dup = 0; $bound = 0
foreach ($id in ($bodies + ($partsByMachine.Values | ForEach-Object { $_ }))) {
    $els = Load-Elems $id
    for ($i = 0; $i -lt $els.Count; $i++) {
        $a = $els[$i]
        if (($a.tx - $a.fx) -lt $eps -or ($a.ty - $a.fy) -lt $eps -or ($a.tz - $a.fz) -lt $eps) {
            Write-Host ("ZERO  {0} #{1}" -f $id, $i); $zero++
        }
        if ($a.tx -gt 32 -or $a.ty -gt 32 -or $a.tz -gt 32 -or $a.fx -lt -16 -or $a.fy -lt -16 -or $a.fz -lt -16) {
            Write-Host ("BOUND {0} #{1}  ({2},{3},{4})-({5},{6},{7})  坐标必须在 [-16,32]" -f $id, $i, $a.fx, $a.fy, $a.fz, $a.tx, $a.ty, $a.tz); $bound++
        }
        for ($j = $i + 1; $j -lt $els.Count; $j++) {
            $b = $els[$j]
            if ($a.fx -eq $b.fx -and $a.fy -eq $b.fy -and $a.fz -eq $b.fz -and $a.tx -eq $b.tx -and $a.ty -eq $b.ty -and $a.tz -eq $b.tz) {
                Write-Host ("DUP   {0} #{1}=#{2}" -f $id, $i, $j); $dup++; continue
            }
            $aa = @(@($a.fx, $a.fy, $a.fz), @($a.tx, $a.ty, $a.tz))
            $bb = @(@($b.fx, $b.fy, $b.fz), @($b.tx, $b.ty, $b.tz))
            for ($ax = 0; $ax -lt 3; $ax++) {
                $o1 = ($ax + 1) % 3; $o2 = ($ax + 2) % 3
                $ov1 = [Math]::Min($aa[1][$o1], $bb[1][$o1]) - [Math]::Max($aa[0][$o1], $bb[0][$o1])
                $ov2 = [Math]::Min($aa[1][$o2], $bb[1][$o2]) - [Math]::Max($aa[0][$o2], $bb[0][$o2])
                if ($ov1 -le 0.01 -or $ov2 -le 0.01) { continue }
                $same = ([Math]::Abs($aa[0][$ax] - $bb[0][$ax]) -lt $eps) -or ([Math]::Abs($aa[1][$ax] - $bb[1][$ax]) -lt $eps)
                if ($same) {
                    Write-Host ("FIGHT {0} #{1} #{2} axis={3}  A=({4},{5},{6})-({7},{8},{9}) B=({10},{11},{12})-({13},{14},{15})" -f $id, $i, $j, $ax, $a.fx, $a.fy, $a.fz, $a.tx, $a.ty, $a.tz, $b.fx, $b.fy, $b.fz, $b.tx, $b.ty, $b.tz); $fight++
                }
            }
        }
    }
}

# 穿模：机体 vs 可动部件（同机）—— 体积重叠即旋转时可能撞上
$clip = 0
foreach ($m in $bodies) {
    $bel = Load-Elems $m
    foreach ($partId in $partsByMachine[$m]) {
        $pel = Load-Elems $partId
        foreach ($a in $bel) {
            foreach ($b in $pel) {
                $ox = [Math]::Min($a.tx, $b.tx) - [Math]::Max($a.fx, $b.fx)
                $oy = [Math]::Min($a.ty, $b.ty) - [Math]::Max($a.fy, $b.fy)
                $oz = [Math]::Min($a.tz, $b.tz) - [Math]::Max($a.fz, $b.fz)
                if ($ox -gt 0.05 -and $oy -gt 0.05 -and $oz -gt 0.05) {
                    Write-Host ("CLIP  {0} 机({1},{2},{3})-({4},{5},{6}) vs {7}({8},{9},{10})-({11},{12},{13})" -f $m, $a.fx, $a.fy, $a.fz, $a.tx, $a.ty, $a.tz, $partId, $b.fx, $b.fy, $b.fz, $b.tx, $b.ty, $b.tz); $clip++
                }
            }
        }
    }
}

Write-Host ("==== fight={0}  zero={1}  dup={2}  clip={3}  bound={4}" -f $fight, $zero, $dup, $clip, $bound)

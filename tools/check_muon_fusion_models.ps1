# check_muon_fusion_models.ps1 —— 缪子聚变仪模型静态自检。
#
#   · UV 合法性：每个面必须恰好 4 个值、且在 0..16 之间
#   · element 范围：from / to 必须落在 -16..32
#   · 共面 z-fighting：同轴、同平面、同法线且面积重叠的面（应为 0 处）
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/check_muon_fusion_models.ps1
$ErrorActionPreference = 'Stop'
[Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::InvariantCulture

$root   = Split-Path -Parent $PSScriptRoot
$models = Join-Path $root 'src\main\resources\assets\stellar_evolution\models\block'
$names  = @('muon_fusion', 'muon_fusion_item') + (0..17 | ForEach-Object { "muon_bolt_$_" })

$problems = 0
$totalFaces = 0
$totalElements = 0

foreach ($name in $names) {
    $path = Join-Path $models "$name.json"
    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "[missing] $name.json"
        $problems++
        continue
    }
    $m = Get-Content -Raw -LiteralPath $path | ConvertFrom-Json

    # ---- UV / 范围 ----
    foreach ($e in $m.elements) {
        $totalElements++
        for ($axis = 0; $axis -lt 3; $axis++) {
            if ($e.from[$axis] -lt -16 -or $e.from[$axis] -gt 32 -or $e.to[$axis] -lt -16 -or $e.to[$axis] -gt 32) {
                Write-Host "[range] $name : element out of -16..32"
                $problems++
            }
        }
        foreach ($p in $e.faces.PSObject.Properties) {
            $uv = $p.Value.uv
            $totalFaces++
            if ($uv.Count -ne 4) {
                Write-Host "[uv] $name : $($p.Name) 面有 $($uv.Count) 个 uv 值"
                $problems++
                continue
            }
            foreach ($n in $uv) {
                if ($n -lt 0 -or $n -gt 16) {
                    Write-Host "[uv] $name : $($p.Name) 面 uv 越界（$n）"
                    $problems++
                    break
                }
            }
        }
    }

    # ---- 共面冲突 ----
    $faces = New-Object System.Collections.ArrayList
    $i = 0
    foreach ($e in $m.elements) {
        $f = $e.from
        $t = $e.to
        $sets = @(
            @('x', $f[0], -1, $f[1], $t[1], $f[2], $t[2]),
            @('x', $t[0], 1, $f[1], $t[1], $f[2], $t[2]),
            @('y', $f[1], -1, $f[0], $t[0], $f[2], $t[2]),
            @('y', $t[1], 1, $f[0], $t[0], $f[2], $t[2]),
            @('z', $f[2], -1, $f[0], $t[0], $f[1], $t[1]),
            @('z', $t[2], 1, $f[0], $t[0], $f[1], $t[1])
        )
        foreach ($s in $sets) {
            [void]$faces.Add([pscustomobject]@{
                idx = $i; axis = $s[0]; plane = [double]$s[1]; sign = [int]$s[2]
                a1 = [double]$s[3]; a2 = [double]$s[4]; b1 = [double]$s[5]; b2 = [double]$s[6]
            })
        }
        $i++
    }
    $groups = $faces | Group-Object { "{0}|{1:N3}|{2}" -f $_.axis, $_.plane, $_.sign }
    foreach ($g in $groups) {
        if ($g.Count -lt 2) { continue }
        $list = @($g.Group)
        for ($a = 0; $a -lt $list.Count - 1; $a++) {
            for ($b = $a + 1; $b -lt $list.Count; $b++) {
                $p = $list[$a]
                $q = $list[$b]
                $oa = [Math]::Min($p.a2, $q.a2) - [Math]::Max($p.a1, $q.a1)
                $ob = [Math]::Min($p.b2, $q.b2) - [Math]::Max($p.b1, $q.b1)
                if ($oa -gt 0.001 -and $ob -gt 0.001) {
                    Write-Host ("[coplanar] $name : elem {0} & {1} @ {2}={3:N3}" -f $p.idx, $q.idx, $p.axis, $p.plane)
                    $problems++
                }
            }
        }
    }
}

Write-Host "checked $($names.Count) models · $totalElements elements · $totalFaces faces · problems: $problems"
if ($problems -gt 0) { exit 1 }

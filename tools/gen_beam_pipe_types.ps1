# gen_beam_pipe_types.ps1 —— 让束流管的中心 2×2 按「流类型」显示不同颜色。
#
# 口径：以 models/block/beam_pipe/core.json 为模板，只替换 #core 的贴图，
#       生成 core_<type>.json（9 种）；blockstate 按 stream 值选择对应的 core 模型。
#       外皮钨块与六个方向的 arm 不变。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_beam_pipe_types.ps1
$ErrorActionPreference = 'Stop'

$ProjRoot = Split-Path -Parent $PSScriptRoot
$ModelDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\block\beam_pipe'
$StateDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\blockstates'
$UTF8 = New-Object System.Text.UTF8Encoding($false)

# 流类型（顺序必须与 StreamType 的 ordinal 一致：muon=0 … radioactive_ion=8）
$types = [ordered]@{
    muon             = 'minecraft:block/cyan_concrete'
    proton           = 'minecraft:block/red_concrete'
    deuteron         = 'minecraft:block/orange_concrete'
    alpha            = 'minecraft:block/yellow_concrete'
    neutron          = 'minecraft:block/green_concrete'
    lead_ion         = 'minecraft:block/purple_concrete'
    uranium_ion      = 'minecraft:block/blue_concrete'
    polarized_proton = 'minecraft:block/pink_concrete'
    radioactive_ion  = 'minecraft:block/lime_concrete'
}

$base = Get-Content -Raw (Join-Path $ModelDir 'core.json')

foreach ($key in $types.Keys) {
    $text = $base.Replace('minecraft:block/polished_andesite', $types[$key])
    $path = Join-Path $ModelDir ("core_{0}.json" -f $key)
    [System.IO.File]::WriteAllText($path, $text, $UTF8)
    Write-Host ("  写出 core_{0}.json" -f $key)
}

# ── blockstate：按 stream 值选 core 模型 + 六个方向按连接叠 arm ──
$parts = New-Object System.Collections.Generic.List[object]
$index = 0
foreach ($key in $types.Keys) {
    $when = New-Object System.Management.Automation.PSObject
    $when | Add-Member -MemberType NoteProperty -Name 'stream' -Value "$index"
    $apply = New-Object System.Management.Automation.PSObject
    $apply | Add-Member -MemberType NoteProperty -Name 'model' -Value "stellar_evolution:block/beam_pipe/core_$key"
    $parts.Add([pscustomobject]@{ when = $when; apply = $apply })
    $index++
}

# 空管（stream = 流种类数）：保持原版安山岩中心
$emptyWhen = New-Object System.Management.Automation.PSObject
$emptyWhen | Add-Member -MemberType NoteProperty -Name 'stream' -Value "$index"
$emptyApply = New-Object System.Management.Automation.PSObject
$emptyApply | Add-Member -MemberType NoteProperty -Name 'model' -Value 'stellar_evolution:block/beam_pipe/core'
$parts.Add([pscustomobject]@{ when = $emptyWhen; apply = $emptyApply })

$armSpecs = @(
    @{ when = 'north'; rot = @{} },
    @{ when = 'east';  rot = @{ y = 90 } },
    @{ when = 'south'; rot = @{ y = 180 } },
    @{ when = 'west';  rot = @{ y = 270 } },
    @{ when = 'up';    rot = @{ x = 270 } },
    @{ when = 'down';  rot = @{ x = 90 } }
)
foreach ($spec in $armSpecs) {
    $when = New-Object System.Management.Automation.PSObject
    $when | Add-Member -MemberType NoteProperty -Name $spec.when -Value 'true'
    $apply = New-Object System.Management.Automation.PSObject
    $apply | Add-Member -MemberType NoteProperty -Name 'model' -Value 'stellar_evolution:block/beam_pipe/arm'
    $apply | Add-Member -MemberType NoteProperty -Name 'uvlock' -Value $true
    foreach ($axis in $spec.rot.Keys) {
        $apply | Add-Member -MemberType NoteProperty -Name $axis -Value $spec.rot[$axis]
    }
    $parts.Add([pscustomobject]@{ when = $when; apply = $apply })
}

$statePath = Join-Path $StateDir 'beam_pipe.json'
[System.IO.File]::WriteAllText($statePath,
    ([pscustomobject]@{ multipart = $parts.ToArray() } | ConvertTo-Json -Depth 10), $UTF8)
Write-Host '束流管 9 种流配色已生成。'

# gen_pulse_core.ps1 —— 从存档结构 pulse_core.nbt 的 5x5x5 单元表生成方块模型。
# 调色板：红石块 / 铜块 / 切制铜楼梯（楼梯贴图取 cut_copper），全部原版贴图。
# 每个结构格 = 16/5 = 3.2px；楼梯按 facing + half 展开成两块板（照原版 stairs 模型口径）。
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outPath = Join-Path $root 'src\main\resources\assets\stellar_evolution\models\block\pulse_core.json'

$step = 16.0 / 5.0

# 5 层（y=0..4），每层 5 行(z) x 5 列(x)。记号：
#   '.' 空气   'R' 红石块   'C' 铜块
#   'N''S''E''W' = 下半楼梯，坡朝北/南/东/西；'n''s''e''w' = 上半楼梯（同朝向）
# 数据来自 pulse_core.nbt 的逐格 palette 索引：
#   3=bottom/north 4=bottom/south 5=bottom/west 6=bottom/east
#   7=top/north    8=top/south    9=top/west    10=top/east
$layers = @(
    @('RC.CR', 'C...C', '.....', 'C...C', 'RC.CR'),
    @('CW.EC', 'NR.RN', '..C..', 'SR.RS', 'CW.EC'),
    @('.....', '..C..', '.CCC.', '..C..', '.....'),
    @('Cw.eC', 'nR.Rn', '..C..', 'sR.Rs', 'Cw.eC'),
    @('RC.CR', 'C...C', '.....', 'C...C', 'RC.CR')
)

function Num([double]$v) {
    $r = [Math]::Round($v, 5)
    if ($r -eq [Math]::Floor($r)) { return [int]$r }
    return $r
}

$elements = New-Object System.Collections.Generic.List[object]

function Add-Box([string]$tex, [double]$x0, [double]$y0, [double]$z0, [double]$x1, [double]$y1, [double]$z1) {
    $cull = @{
        down  = ($y0 -eq 0);  up    = ($y1 -eq 16)
        north = ($z0 -eq 0);  south = ($z1 -eq 16)
        west  = ($x0 -eq 0);  east  = ($x1 -eq 16)
    }
    $faces = [ordered]@{}
    foreach ($f in @('down', 'up', 'north', 'south', 'west', 'east')) {
        $face = [ordered]@{ texture = $tex }
        if ($cull[$f]) { $face['cullface'] = $f }
        $faces[$f] = $face
    }
    $elements.Add([ordered]@{
            from  = @((Num $x0), (Num $y0), (Num $z0))
            to    = @((Num $x1), (Num $y1), (Num $z1))
            faces = $faces
        })
}

for ($y = 0; $y -lt 5; $y++) {
    for ($z = 0; $z -lt 5; $z++) {
        $row = $layers[$y][$z]
        for ($x = 0; $x -lt 5; $x++) {
            $ch = $row[$x]
            if ($ch -eq '.') { continue }
            $x0 = $x * $step; $x1 = $x0 + $step
            $y0 = $y * $step; $y1 = $y0 + $step
            $z0 = $z * $step; $z1 = $z0 + $step
            $hx = $x0 + $step / 2.0
            $hy = $y0 + $step / 2.0
            $hz = $z0 + $step / 2.0
            switch -CaseSensitive ($ch) {
                'R' { Add-Box '#redstone' $x0 $y0 $z0 $x1 $y1 $z1 }
                'C' { Add-Box '#copper' $x0 $y0 $z0 $x1 $y1 $z1 }
                # 下半楼梯：整格下半的底板 + 朝 facing 那半格的上半
                'N' { Add-Box '#stairs' $x0 $y0 $z0 $x1 $hy $z1; Add-Box '#stairs' $x0 $hy $z0 $x1 $y1 $hz }
                'S' { Add-Box '#stairs' $x0 $y0 $z0 $x1 $hy $z1; Add-Box '#stairs' $x0 $hy $hz $x1 $y1 $z1 }
                'W' { Add-Box '#stairs' $x0 $y0 $z0 $x1 $hy $z1; Add-Box '#stairs' $x0 $hy $z0 $hx $y1 $z1 }
                'E' { Add-Box '#stairs' $x0 $y0 $z0 $x1 $hy $z1; Add-Box '#stairs' $hx $hy $z0 $x1 $y1 $z1 }
                # 上半楼梯：整格上半的顶板 + 朝 facing 那半格的下半（= 下半楼梯 x 旋转 180）
                'n' { Add-Box '#stairs' $x0 $hy $z0 $x1 $y1 $z1; Add-Box '#stairs' $x0 $y0 $z0 $x1 $hy $hz }
                's' { Add-Box '#stairs' $x0 $hy $z0 $x1 $y1 $z1; Add-Box '#stairs' $x0 $y0 $hz $x1 $hy $z1 }
                'w' { Add-Box '#stairs' $x0 $hy $z0 $x1 $y1 $z1; Add-Box '#stairs' $x0 $y0 $z0 $hx $hy $z1 }
                'e' { Add-Box '#stairs' $x0 $hy $z0 $x1 $y1 $z1; Add-Box '#stairs' $hx $y0 $z0 $x1 $hy $z1 }
            }
        }
    }
}

$model = [ordered]@{
    parent   = 'minecraft:block/block'
    textures = [ordered]@{
        redstone = 'minecraft:block/redstone_block'
        copper   = 'minecraft:block/copper_block'
        stairs   = 'minecraft:block/cut_copper'
        particle = 'minecraft:block/copper_block'
    }
    elements = $elements
}

$json = $model | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("elements = " + $elements.Count)
Write-Output ("written  -> " + $outPath)

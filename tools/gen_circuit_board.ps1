# gen_circuit_board.ps1 —— 从存档结构 circuit_board.nbt 的 11x3x8 单元表生成方块模型。
# 调色板：红石块 / 抛光闪长岩 / 铜块 / 赭黄蛙明灯 / 避雷针 / 空气（全部原版贴图）。
# 整座缩进 1 格：x 16/11、z 16/8 px；高度只有**半格（8px）**，即一块「半砖」大小的电路板。
# 相邻两格都是「实心整方块」时，中间那面直接不生成（被挡住），显著减小模型体积。
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$outPath = Join-Path $root 'src\main\resources\assets\stellar_evolution\models\block\circuit_board.json'

$SX = 16.0 / 11.0
$SY = 8.0 / 3.0
$SZ = 16.0 / 8.0

# 3 层（y=0..2），每层 8 行(z) x 11 列(x)。记号：
#   '.' 空气   'R' 红石块   'D' 抛光闪长岩   'C' 铜块   'F' 蛙明灯
#   'S' / 'N' 避雷针（facing=south / north，水平指向 ±Z）
# 数据来自 circuit_board.nbt 的逐格 palette 索引（3/4=蛙明灯 axis y/z，6/7=避雷针 facing south/north）。
$layers = @(
    @(
        '..C..C..C..',
        'RDDDDDDDDDC',
        'CDCDCDCDCDC',
        'CDCDCDCDCDC',
        'CDCDCDCDCDC',
        'CDCDCDCDCDC',
        'CDDDDDDDDDR',
        '....C..C...'
    ),
    @(
        '..C..C..C..',
        'RDCCCCCCCDC',
        'RDCDCDCDCDC',
        '.CRCRCRCRC.',
        '.CRCCCCCRC.',
        'CDFCCFRRRDR',
        'CDCCCCCCCDR',
        '....C..C...'
    ),
    @(
        '..C..C..C..',
        '..C..C..C..',
        '..S..S..C..',
        '........S..',
        '...........',
        '....N..N...',
        '....C..C...',
        '....C..C...'
    )
)

function Num([double]$v) {
    $r = [Math]::Round($v, 5)
    if ($r -eq [Math]::Floor($r)) { return [int]$r }
    return $r
}

function At([int]$x, [int]$y, [int]$z) {
    if ($x -lt 0 -or $x -gt 10 -or $y -lt 0 -or $y -gt 2 -or $z -lt 0 -or $z -gt 7) { return '.' }
    return $layers[$y][$z][$x]
}

# 实心整方块（用于「相邻就隐藏中间面」的判定；避雷针是细杆，不算）
function Solid([int]$x, [int]$y, [int]$z) {
    $c = At $x $y $z
    return ($c -eq 'R' -or $c -eq 'D' -or $c -eq 'C' -or $c -eq 'F')
}

$elements = New-Object System.Collections.Generic.List[object]

# 六个方向 + 该面若落在方块边界时的 cullface 名
$dirs = @(
    @{ key = 'down';  dx = 0;  dy = -1; dz = 0;  plane = 'y0'; cf = 'down' },
    @{ key = 'up';    dx = 0;  dy = 1;  dz = 0;  plane = 'y1'; cf = 'up' },
    @{ key = 'north'; dx = 0;  dy = 0;  dz = -1; plane = 'z0'; cf = 'north' },
    @{ key = 'south'; dx = 0;  dy = 0;  dz = 1;  plane = 'z1'; cf = 'south' },
    @{ key = 'west';  dx = -1; dy = 0;  dz = 0;  plane = 'x0'; cf = 'west' },
    @{ key = 'east';  dx = 1;  dy = 0;  dz = 0;  plane = 'x1'; cf = 'east' }
)

function Add-Box([double]$x0, [double]$y0, [double]$z0, [double]$x1, [double]$y1, [double]$z1,
    [int]$cx, [int]$cy, [int]$cz, [string]$tex, [string]$topTex, [bool]$cullNeighbors) {
    $faces = [ordered]@{}
    foreach ($d in $dirs) {
        if ($cullNeighbors -and (Solid ($cx + $d.dx) ($cy + $d.dy) ($cz + $d.dz))) { continue }
        $face = [ordered]@{ texture = $tex }
        if ($topTex -and ($d.key -eq 'down' -or $d.key -eq 'up')) { $face['texture'] = $topTex }
        $onBoundary = switch ($d.plane) { 'x0' { $x0 -eq 0 } 'x1' { $x1 -eq 16 } 'y0' { $y0 -eq 0 } 'y1' { $y1 -eq 16 } 'z0' { $z0 -eq 0 } 'z1' { $z1 -eq 16 } }
        if ($onBoundary) { $face['cullface'] = $d.cf }
        $faces[$d.key] = $face
    }
    if ($faces.Count -eq 0) { return }
    $elements.Add([ordered]@{
            from  = @((Num $x0), (Num $y0), (Num $z0))
            to    = @((Num $x1), (Num $y1), (Num $z1))
            faces = $faces
        })
}

for ($y = 0; $y -lt 3; $y++) {
    for ($z = 0; $z -lt 8; $z++) {
        for ($x = 0; $x -lt 11; $x++) {
            $ch = $layers[$y][$z][$x]
            if ($ch -eq '.') { continue }
            $x0 = $x * $SX; $x1 = $x0 + $SX
            $y0 = $y * $SY; $y1 = $y0 + $SY
            $z0 = $z * $SZ; $z1 = $z0 + $SZ
            switch -CaseSensitive ($ch) {
                'R' { Add-Box $x0 $y0 $z0 $x1 $y1 $z1 $x $y $z '#redstone' $null $true }
                'D' { Add-Box $x0 $y0 $z0 $x1 $y1 $z1 $x $y $z '#diorite' $null $true }
                'C' { Add-Box $x0 $y0 $z0 $x1 $y1 $z1 $x $y $z '#copper' $null $true }
                'F' { Add-Box $x0 $y0 $z0 $x1 $y1 $z1 $x $y $z '#froglight_side' '#froglight_top' $true }
                # 避雷针：细横杆，占格内中央一条（朝向 ±Z 差别在这么小的格子里看不出来，同形处理）
                'S' { Add-Box ($x0 + $SX * 0.3) ($y0 + $SY * 0.3) $z0 ($x0 + $SX * 0.7) ($y0 + $SY * 0.7) $z1 $x $y $z '#rod' $null $false }
                'N' { Add-Box ($x0 + $SX * 0.3) ($y0 + $SY * 0.3) $z0 ($x0 + $SX * 0.7) ($y0 + $SY * 0.7) $z1 $x $y $z '#rod' $null $false }
            }
        }
    }
}

$model = [ordered]@{
    parent   = 'minecraft:block/block'
    textures = [ordered]@{
        redstone        = 'minecraft:block/redstone_block'
        diorite         = 'minecraft:block/polished_diorite'
        copper          = 'minecraft:block/copper_block'
        froglight_top   = 'minecraft:block/ochre_froglight_top'
        froglight_side  = 'minecraft:block/ochre_froglight_side'
        rod             = 'minecraft:block/lightning_rod'
        particle        = 'minecraft:block/polished_diorite'
    }
    elements = $elements
}

$json = $model | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($outPath, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("elements = " + $elements.Count)
Write-Output ("written  -> " + $outPath)

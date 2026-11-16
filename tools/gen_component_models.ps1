﻿# gen_component_models.ps1 —— 为「重工业组件物品」生成 3D 物品模型。
#
# 3D 物品模型 = 由若干立方体（elements）拼出几何形体，物品栏里能看到多个面；
# 各面直接复用原版方块贴图（iron_block / copper_block / gold_block / 彩色混凝土…），
# 不依赖平面 16×16 图标（未使用的素材已清理）。
#
# 输出：
#   src/main/resources/assets/stellar_evolution/models/item/<id>.json   3D 几何（parent = minecraft:block/block，带物品显示变换）
#   src/main/resources/assets/stellar_evolution/items/<id>.json         物品模型定义（type = minecraft:model）
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_component_models.ps1
$ErrorActionPreference = 'Stop'

$ProjRoot  = Split-Path -Parent $PSScriptRoot
$ModelsDir = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
New-Item -ItemType Directory -Force -Path $ModelsDir, $ItemsDir | Out-Null

$FACES = 'down', 'up', 'north', 'south', 'west', 'east'

function New-Element($from, $to, $tex) {
    # PS 5.1 的 ConvertTo-Json 不能序列化「嵌套哈希表」，一律用 PSCustomObject
    # 逐个 Add-Member 建对象：PS 5.1 里哈希表直接转 PSCustomObject 会串味
    $faceObj = New-Object System.Management.Automation.PSObject
    foreach ($f in $FACES) {
        $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value ([pscustomobject]@{ texture = "#$tex" })
    }
    return [pscustomobject]@{ from = $from; to = $to; faces = $faceObj }
}

# 每件：textures = 材质别名 -> 原版方块贴图；boxes = 若干 (from, to, 材质别名)。
# 注意：电路板的物品 id 用 pcb —— 方块 circuit_board 已占用同名物品（其 BlockItem）。
$specs = @{

    'electric_motor' = @{
        textures = @{
            body = 'minecraft:block/iron_block'
            cu   = 'minecraft:block/copper_block'
        }
        boxes = @(
            @{ from = @(2, 5, 4);      to = @(12, 11, 12);     tex = 'body' }   # 横放机体
            @{ from = @(5.6, 4.6, 3.6); to = @(9.4, 11.4, 12.4); tex = 'cu' }   # 铜绕组环带
            @{ from = @(11.6, 5.2, 4.2); to = @(12.6, 10.8, 11.8); tex = 'cu' } # 端盖
            @{ from = @(12.6, 7, 7.4); to = @(14.6, 9, 8.6);   tex = 'body' }   # 输出轴
            @{ from = @(3, 11, 6);     to = @(6, 13.2, 10);    tex = 'body' }   # 顶部接线盒
            @{ from = @(3, 3.4, 5);    to = @(5, 5, 11);       tex = 'body' }   # 左底脚
            @{ from = @(9, 3.4, 5);    to = @(11, 5, 11);      tex = 'body' }   # 右底脚
        )
    }

    'flux_coil' = @{
        textures = @{
            core = 'minecraft:block/iron_block'
            cu   = 'minecraft:block/copper_block'
        }
        boxes = @(
            @{ from = @(1, 7, 7);     to = @(15, 9, 9);       tex = 'core' }    # 铁芯
            @{ from = @(3, 5.4, 5.4); to = @(5, 10.6, 10.6);  tex = 'cu' }      # 绕组 1
            @{ from = @(6, 5.4, 5.4); to = @(8, 10.6, 10.6);  tex = 'cu' }      # 绕组 2
            @{ from = @(9, 5.4, 5.4); to = @(11, 10.6, 10.6); tex = 'cu' }      # 绕组 3
            @{ from = @(12, 5.4, 5.4); to = @(14, 10.6, 10.6); tex = 'cu' }     # 绕组 4
        )
    }

    # 加热线圈 / 高频线圈：照磁通线圈的造型，只把绕组换成钨（耐高温）/ 金（高频低阻），铁芯换钢。
    'heating_coil' = @{
        textures = @{
            core = 'stellar_evolution:block/steel_block'
            w    = 'stellar_evolution:block/tungsten_block'
        }
        boxes = @(
            @{ from = @(1, 7, 7);     to = @(15, 9, 9);       tex = 'core' }    # 钢芯
            @{ from = @(3, 5.4, 5.4); to = @(5, 10.6, 10.6);  tex = 'w' }       # 钨绕组 1
            @{ from = @(6, 5.4, 5.4); to = @(8, 10.6, 10.6);  tex = 'w' }       # 钨绕组 2
            @{ from = @(9, 5.4, 5.4); to = @(11, 10.6, 10.6); tex = 'w' }       # 钨绕组 3
            @{ from = @(12, 5.4, 5.4); to = @(14, 10.6, 10.6); tex = 'w' }      # 钨绕组 4
        )
    }

    'high_frequency_coil' = @{
        textures = @{
            core = 'stellar_evolution:block/steel_block'
            au   = 'minecraft:block/gold_block'
        }
        boxes = @(
            @{ from = @(1, 7, 7);     to = @(15, 9, 9);       tex = 'core' }    # 钢芯
            @{ from = @(3, 5.4, 5.4); to = @(5, 10.6, 10.6);  tex = 'au' }      # 金绕组 1
            @{ from = @(6, 5.4, 5.4); to = @(8, 10.6, 10.6);  tex = 'au' }      # 金绕组 2
            @{ from = @(9, 5.4, 5.4); to = @(11, 10.6, 10.6); tex = 'au' }      # 金绕组 3
            @{ from = @(12, 5.4, 5.4); to = @(14, 10.6, 10.6); tex = 'au' }     # 金绕组 4
        )
    }

    'piston_assembly' = @{
        textures = @{
            body = 'minecraft:block/iron_block'
            cu   = 'minecraft:block/copper_block'
        }
        boxes = @(
            @{ from = @(3, 4.5, 3);   to = @(13, 12, 13);      tex = 'body' }   # 缸体
            @{ from = @(3.6, 12, 3.6); to = @(12.4, 14, 12.4); tex = 'cu' }     # 活塞顶
            @{ from = @(7, 1.6, 7);   to = @(9, 5, 9);         tex = 'body' }   # 连杆
            @{ from = @(6.4, 1, 6.4); to = @(9.6, 2.6, 9.6);   tex = 'cu' }     # 连杆大头销座
        )
    }

    'pcb' = @{
        textures = @{
            board = 'minecraft:block/green_concrete'
            dark  = 'minecraft:block/black_concrete'
            cu    = 'minecraft:block/copper_block'
            gold  = 'minecraft:block/gold_block'
        }
        boxes = @(
            @{ from = @(2, 6, 2);     to = @(14, 8, 14);       tex = 'board' }  # 板
            @{ from = @(6, 8, 5);     to = @(10, 10, 11);      tex = 'dark' }   # 芯片
            @{ from = @(3, 8, 3);     to = @(6, 8.3, 4);       tex = 'cu' }     # 走线
            @{ from = @(3, 8, 6);     to = @(4, 8.3, 12);      tex = 'cu' }     # 走线
            @{ from = @(11, 8, 4);    to = @(13, 8.3, 12);     tex = 'cu' }     # 走线
            @{ from = @(3, 8, 12);    to = @(4.5, 8.3, 13.5);  tex = 'gold' }   # 焊盘
            @{ from = @(11.5, 8, 12); to = @(13, 8.3, 13.5);   tex = 'gold' }   # 焊盘
        )
    }

    'chip' = @{
        textures = @{
            dark = 'minecraft:block/black_concrete'
            lead = 'minecraft:block/iron_block'
            mark = 'minecraft:block/light_gray_concrete'
        }
        boxes = @(
            @{ from = @(4, 6, 5);     to = @(12, 10, 11);      tex = 'dark' }   # 近黑本体
            @{ from = @(4.6, 10, 5.6); to = @(5.6, 10.4, 6.6); tex = 'mark' }   # 一脚点
            @{ from = @(1, 7, 5.6);   to = @(4, 8, 6.8);       tex = 'lead' }   # 左引脚
            @{ from = @(1, 7, 7.6);   to = @(4, 8, 8.8);       tex = 'lead' }
            @{ from = @(1, 7, 9.6);   to = @(4, 8, 10.8);      tex = 'lead' }
            @{ from = @(12, 7, 5.6);  to = @(15, 8, 6.8);      tex = 'lead' }   # 右引脚
            @{ from = @(12, 7, 7.6);  to = @(15, 8, 8.8);      tex = 'lead' }
            @{ from = @(12, 7, 9.6);  to = @(15, 8, 10.8);     tex = 'lead' }
        )
    }

    'basic_fluid_cell' = @{
        textures = @{
            body = 'minecraft:block/copper_block'
            cu   = 'minecraft:block/iron_block'
            seal = 'minecraft:block/brown_concrete'
        }
        boxes = @(
            @{ from = @(4, 3, 4);       to = @(12, 13, 12);     tex = 'body' } # 铜罐体
            @{ from = @(3.7, 12.4, 3.7); to = @(12.3, 13.4, 12.3); tex = 'cu' } # 上箍
            @{ from = @(3.7, 2.4, 3.7);  to = @(12.3, 3.4, 12.3);  tex = 'cu' } # 下箍
            @{ from = @(6, 13.4, 6);     to = @(10, 15.4, 10);   tex = 'cu' }   # 顶部阀口
            @{ from = @(4.4, 6, 3.6);    to = @(11.6, 10, 4.4);  tex = 'seal' } # 密封条
        )
    }

    'fluid_cell' = @{
        textures = @{
            body  = 'minecraft:block/iron_block'
            cu    = 'minecraft:block/copper_block'
            fluid = 'minecraft:block/light_blue_concrete'
        }
        boxes = @(
            @{ from = @(4, 3, 4);     to = @(12, 13, 12);      tex = 'body' }   # 罐体
            @{ from = @(3.7, 12.4, 3.7); to = @(12.3, 13.4, 12.3); tex = 'cu' } # 上箍
            @{ from = @(3.7, 2.4, 3.7);  to = @(12.3, 3.4, 12.3);  tex = 'cu' } # 下箍
            @{ from = @(6, 13.4, 6);  to = @(10, 15.4, 10);    tex = 'body' }   # 顶部阀口
            @{ from = @(4.4, 6, 3.6); to = @(11.6, 10, 4.4);   tex = 'fluid' }  # 流体视窗
        )
    }

    'rare_earth_magnet' = @{
        textures = @{
            body = 'minecraft:block/iron_block'
            cu   = 'minecraft:block/copper_block'
        }
        boxes = @(
            @{ from = @(3, 3, 4);     to = @(13, 6, 12);     tex = 'body' },   # 底座
            @{ from = @(3, 6, 4);     to = @(6, 13, 12);     tex = 'body' },   # 左极
            @{ from = @(10, 6, 4);    to = @(13, 13, 12);    tex = 'body' },   # 右极
            @{ from = @(5.4, 7, 4.4); to = @(10.6, 9, 11.6); tex = 'cu' }      # 铜线圈
        )
    }

    'platinum_catalyst' = @{
        textures = @{
            frame = 'minecraft:block/iron_block'
            mesh  = 'minecraft:block/light_gray_concrete'
        }
        boxes = @(
            @{ from = @(3, 3, 3);       to = @(13, 4.6, 13);    tex = 'frame' },  # 底框
            @{ from = @(3, 11.4, 3);    to = @(13, 13, 13);     tex = 'frame' },  # 顶框
            @{ from = @(4.6, 4.6, 4.6);  to = @(5.8, 11.4, 11.4); tex = 'mesh' }, # 网片 1
            @{ from = @(7.4, 4.6, 4.6);  to = @(8.6, 11.4, 11.4); tex = 'mesh' }, # 网片 2
            @{ from = @(10.2, 4.6, 4.6); to = @(11.4, 11.4, 11.4); tex = 'mesh' } # 网片 3
        )
    }
}

foreach ($id in $specs.Keys) {
    $spec = $specs[$id]
    $elements = @()
    foreach ($b in $spec.boxes) { $elements += New-Element $b.from $b.to $b.tex }

    # 补一个 particle（破坏粒子用）：3D 物品模型少了它会报「Missing texture references: particle」
    $tex = @{}
    foreach ($k in $spec.textures.Keys) { $tex[$k] = $spec.textures[$k] }
    $tex['particle'] = $spec.textures[$spec.boxes[0].tex]

    $model = [pscustomobject]@{
        parent   = 'minecraft:block/block'
        textures = [pscustomobject]$tex
        elements = $elements
    }
    $modelJson = $model | ConvertTo-Json -Depth 12
    [System.IO.File]::WriteAllText((Join-Path $ModelsDir "$id.json"), $modelJson, (New-Object System.Text.UTF8Encoding($false)))

    $item = [pscustomobject]@{
        model = [pscustomobject]@{
            type  = 'minecraft:model'
            model = "stellar_evolution:item/$id"
        }
    }
    [System.IO.File]::WriteAllText((Join-Path $ItemsDir "$id.json"), ($item | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "  $id  ($($spec.boxes.Count) 个立方体)"
}

Write-Host ("完成：{0} 件 3D 物品模型 -> {1}" -f $specs.Count, $ModelsDir)

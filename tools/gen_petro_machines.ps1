# gen_petro_machines.ps1 —— 石化线单方块机器：机体（2×2×2）+ 可动部件（每台 2 个），照生化线 gen_bio_machines.ps1 口径。
#
# 与生化线的差别：
#   · 不做「接管口（port_* ）」那套（石化机器用大机器框架的「任意一格任意面贴铜块」接入，无需 12 个接口状态）；
#   · 贴图同样全部复用原版（零新增贴图）；坐标 0..32（1 单位 = 1/16 格）。
#
# 可动部件（12 个 → 12 个隐藏载体方块）：
#   distillation_tower_fan / _valve      分馏塔：顶置冷凝风扇 + 底部卸料阀
#   cracking_furnace_fan / _handwheel    裂解炉：烟囱抽风 + 炉门手轮
#   reformer_agitator / _valve           重整器：顶置搅拌轮 + 底部阀门
#   polymerizer_agitator / _coupling     聚合釜：顶置搅拌轮 + 更高位联轴节
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_petro_machines.ps1
$ErrorActionPreference = 'Stop'

$root      = Split-Path -Parent $PSScriptRoot
$assets    = Join-Path $root 'src\main\resources\assets\stellar_evolution'
$modelsDir = Join-Path $assets 'models\block'
$statesDir = Join-Path $assets 'blockstates'
$itemsDir  = Join-Path $assets 'items'
$lootDir   = Join-Path $root 'src\main\resources\data\stellar_evolution\loot_table\blocks'
New-Item -ItemType Directory -Force -Path $modelsDir, $statesDir, $itemsDir, $lootDir | Out-Null

$FACES = 'down', 'up', 'north', 'south', 'west', 'east'
$UTF8  = New-Object System.Text.UTF8Encoding($false)   # 无 BOM（模型 JSON）

function New-Element($from, $to, $tex) {
    # 每个面都写显式 UV，且取贴图中心区域（照生化线口径：不写 uv 时大面会采样到图集里相邻贴图）
    $dx = [double]$to[0] - [double]$from[0]
    $dy = [double]$to[1] - [double]$from[1]
    $dz = [double]$to[2] - [double]$from[2]
    $uvOf = @{
        down  = @([Math]::Min($dx, 16), [Math]::Min($dz, 16))
        up    = @([Math]::Min($dx, 16), [Math]::Min($dz, 16))
        north = @([Math]::Min($dx, 16), [Math]::Min($dy, 16))
        south = @([Math]::Min($dx, 16), [Math]::Min($dy, 16))
        west  = @([Math]::Min($dz, 16), [Math]::Min($dy, 16))
        east  = @([Math]::Min($dz, 16), [Math]::Min($dy, 16))
    }
    $faceObj = New-Object System.Management.Automation.PSObject
    foreach ($f in $FACES) {
        $s = $uvOf[$f]
        $u0 = (16 - $s[0]) / 2
        $v0 = (16 - $s[1]) / 2
        $faceObj | Add-Member -MemberType NoteProperty -Name $f -Value ([pscustomobject]@{
            texture = "#$tex"
            uv      = @($u0, $v0, ($u0 + $s[0]), ($v0 + $s[1]))
        })
    }
    return [pscustomobject]@{ from = $from; to = $to; faces = $faceObj }
}

# 原版贴图别名（零新增贴图）
$T = @{
    copper = 'minecraft:block/copper_block'          # 铜：框架 / 夹套 / 管道 / 部件
    iron   = 'minecraft:block/iron_block'            # 铁：塔身 / 罐体 / 筒体
    dark   = 'minecraft:block/polished_blackstone'   # 深色：炉膛 / 控制盒 / 手轮
    bio    = 'minecraft:block/moss_block'            # 生物质 / 藻膜
    cold   = 'minecraft:block/amethyst_block'        # 晶体
    light  = 'minecraft:block/redstone_block'        # 指示灯 / 炉口
    glow   = 'minecraft:block/magma'                 # 加热辉光
    wireR  = 'minecraft:block/red_concrete'          # 彩色线缆 · 红
    wireY  = 'minecraft:block/yellow_concrete'       # 彩色线缆 · 黄
    wireB  = 'minecraft:block/light_blue_concrete'   # 彩色线缆 · 蓝
    board  = 'minecraft:block/green_concrete'        # 电路板基材
    glass  = 'minecraft:block/glass'                 # 视窗玻璃
    gold   = 'minecraft:block/gold_block'            # 电路走线 / 触点
    # ---- 精修新增（都是原版素材，用来拉开色彩层次） ----
    cuOx   = 'minecraft:block/exposed_copper'            # 氧化铜：管道 / 阀门 / 法兰
    cuOx2  = 'minecraft:block/weathered_copper'          # 更旧一档：老管 / 排污
    cutCu  = 'minecraft:block/cut_copper'                # 切制铜：面板 / 机壳
    cutOx  = 'minecraft:block/exposed_cut_copper'        # 氧化切制铜：锈面板
    chisel = 'minecraft:block/chiseled_copper'           # 雕纹铜：铭牌 / 饰板
    grate  = 'minecraft:block/copper_grate'              # 铜格栅：通风口
    blkBrk = 'minecraft:block/polished_blackstone_bricks' # 黑石砖：炉衬 / 底座
    blkCh  = 'minecraft:block/chiseled_polished_blackstone' # 雕纹黑石：机座饰面
    dTile  = 'minecraft:block/deepslate_tiles'           # 深板岩瓦：基座
    quartz = 'minecraft:block/quartz_block_top'         # 白瓷：表盘 / 绝缘（1.14 起没有 quartz_block 这张贴图）
    neth   = 'minecraft:block/netherite_block'           # 重黑金属：压机 / 螺杆 / 铰链
    white  = 'minecraft:block/white_concrete'            # 白漆：罐体 / 标识
    orange = 'minecraft:block/orange_concrete'           # 警示条
    hose   = 'minecraft:block/black_concrete'            # 黑色软管 / 橡胶
    lamp   = 'minecraft:block/sea_lantern'               # 工作灯
    glowst = 'minecraft:block/glowstone'                 # 柔和辉光
}

# 2×2×2 铜框架：2px 粗；内沿收到 2.5（与机体重叠 0.5，避免共面闪烁）
$frame = @(
    @{ from = @(0, 0, 0);       to = @(2.5, 32, 2.5) },     # 角柱
    @{ from = @(29.5, 0, 0);    to = @(32, 32, 2.5) },
    @{ from = @(0, 0, 29.5);    to = @(2.5, 32, 32) },
    @{ from = @(29.5, 0, 29.5); to = @(32, 32, 32) },
    @{ from = @(2.5, 0, 0);     to = @(29.5, 2, 2.5) },     # 底梁
    @{ from = @(2.5, 0, 29.5);  to = @(29.5, 2, 32) },
    @{ from = @(0, 0, 2.5);     to = @(2.5, 2, 29.5) },
    @{ from = @(29.5, 0, 2.5);  to = @(32, 2, 29.5) },
    @{ from = @(2.5, 30, 0);    to = @(29.5, 32, 2.5) },    # 顶梁
    @{ from = @(2.5, 30, 29.5); to = @(29.5, 32, 32) },
    @{ from = @(0, 30, 2.5);    to = @(2.5, 32, 29.5) },
    @{ from = @(29.5, 30, 2.5); to = @(32, 32, 29.5) }
) | ForEach-Object { @{ from = $_.from; to = $_.to; tex = 'copper' } }

# ── 机体（不含框架 / 不含底部连接件；可动部件的 y 层必须留空，见各处注释） ──────────────
$bodies = [ordered]@{

    # 分馏塔：裙座 + 塔身 + 三道塔盘环 + 顶封头 + 冷凝器 + 侧梯 + 再沸管 + 线缆 + 控制盒
    # 可动部件层：顶 = y31.5..32（上方留空）；底 = y0.3..1.7（裙座从 1.9 起）
    'distillation_tower' = @(
        @{ from = @(4, 1.9, 4);       to = @(28, 3.6, 28);     tex = 'iron' },   # 裙座（与底梁重叠）
        @{ from = @(8, 3.2, 8);       to = @(24, 28.4, 24);    tex = 'iron' },   # 塔身
        @{ from = @(7, 8.4, 7);       to = @(25, 9.8, 25);     tex = 'copper' }, # 塔盘环 1
        @{ from = @(7, 14.6, 7);      to = @(25, 16, 25);      tex = 'copper' }, # 塔盘环 2
        @{ from = @(7, 20.8, 7);      to = @(25, 22.2, 25);    tex = 'copper' }, # 塔盘环 3
        @{ from = @(9, 28, 9);        to = @(23, 29.6, 23);    tex = 'copper' }, # 顶封头
        @{ from = @(10.5, 29.4, 10.5);to = @(21.5, 31, 21.5);  tex = 'dark' },   # 冷凝器
        @{ from = @(9, 30.8, 15);     to = @(23, 31.3, 17);    tex = 'copper' }, # 顶部风扇支梁 ×2
        @{ from = @(15, 30.8, 9);     to = @(17, 31.3, 23);    tex = 'copper' },
        @{ from = @(23.8, 2.6, 14.2); to = @(25.8, 28, 16.8);  tex = 'dark' },   # 侧梯
        @{ from = @(4.2, 4, 10);      to = @(8.2, 26, 12.6);   tex = 'copper' }, # 侧再沸管（伸到塔身内，连成一体）
        @{ from = @(8, 22.4, 7.4);    to = @(24, 23.6, 8.4);   tex = 'wireR' },  # 红色线缆
        @{ from = @(8, 19.8, 7.4);    to = @(24, 21, 8.4);     tex = 'wireB' },  # 蓝色线缆
        @{ from = @(25, 9, 5);        to = @(28.4, 17, 11);    tex = 'dark' },   # 电路控制盒
        @{ from = @(24.6, 9.6, 5.6);  to = @(25.1, 16.4, 10.4);tex = 'board' },  # 电路板面板
        @{ from = @(24.4, 11, 6.6);   to = @(24.9, 11.9, 7.5); tex = 'light' },  # 指示灯
        @{ from = @(24.6, 13, 6.6);   to = @(25.1, 15.4, 7.2); tex = 'gold' }    # 电路走线
    )

    # 裂解炉：炉膛 + 三道加热线圈 + 炉口辉光 + 烟囱 + 炉门 + 线缆 + 控制盒
    # 可动部件层：顶 = y31.4..32（烟囱 31.2 收口）；前 = z0.6..1.9（炉门钢板从 2.1 起）
    'cracking_furnace' = @(
        @{ from = @(4, 1.9, 4);       to = @(28, 26.4, 28);    tex = 'dark' },   # 炉膛
        @{ from = @(3, 7, 3);         to = @(29, 9.4, 29);     tex = 'copper' }, # 加热线圈 1
        @{ from = @(3, 14, 3);        to = @(29, 16.4, 29);    tex = 'copper' }, # 加热线圈 2
        @{ from = @(3, 21, 3);        to = @(29, 23.4, 29);    tex = 'copper' }, # 加热线圈 3
        @{ from = @(9, 9.6, 2.2);     to = @(23, 20.8, 3.2);   tex = 'glow' },   # 炉口辉光（前脸）
        @{ from = @(12, 26, 12);      to = @(20, 31.2, 20);    tex = 'iron' },   # 烟囱
        @{ from = @(10.4, 30.4, 10.4);to = @(21.6, 31.3, 21.6);tex = 'copper' }, # 烟囱罩
        @{ from = @(7, 3, 2.1);       to = @(25, 24, 3.1);     tex = 'iron' },   # 炉门钢板
        @{ from = @(22, 4, 5.4);      to = @(27.6, 12, 9.6);   tex = 'dark' },   # 电路控制盒
        @{ from = @(22.4, 4.6, 6);    to = @(22.9, 11.4, 9);   tex = 'board' },  # 电路板面板
        @{ from = @(22.2, 6, 7);      to = @(22.7, 6.9, 7.9);  tex = 'light' },  # 指示灯
        @{ from = @(8, 21.5, 2.44);   to = @(24, 22.7, 3.44);   tex = 'wireR' },  # 红色线缆（贴炉门钢板，外露 0.4px）
        @{ from = @(8, 23.5, 2.44);   to = @(24, 24.7, 3.44);   tex = 'wireY' },  # 黄色线缆
        @{ from = @(5, 4, 20);        to = @(7, 24, 22.6);     tex = 'copper' }, # 侧烟道
        @{ from = @(25, 14, 20);      to = @(27, 26, 22.6);    tex = 'copper' }  # 侧烟道
    )

    # 重整器：立式反应器 + 顶封头 + 两道夹套 + 侧管 + 顶部总管 + 控制盒
    # 可动部件层：顶 = y28.9..30.9（封头 28.6 收口）；底 = y0.3..1.7（裙座从 1.9 起）
    'reformer' = @(
        @{ from = @(4, 1.9, 4);       to = @(28, 3.6, 28);     tex = 'iron' },   # 裙座
        @{ from = @(8.5, 3.2, 8.5);   to = @(23.5, 28, 23.5);  tex = 'iron' },   # 筒体
        @{ from = @(9.5, 27.6, 9.5);  to = @(22.5, 28.6, 22.5);tex = 'copper' }, # 顶封头（顶部留出搅拌轮层 28.9..30.9）
        @{ from = @(7.5, 8.5, 7.5);   to = @(24.5, 10.6, 24.5);tex = 'copper' }, # 夹套 1
        @{ from = @(7.5, 18, 7.5);    to = @(24.5, 20.1, 24.5);tex = 'copper' }, # 夹套 2
        @{ from = @(5, 4, 10);        to = @(8.6, 26, 12.6);   tex = 'copper' }, # 侧管 ×2（伸到筒体内，连成一体）
        @{ from = @(23.4, 4, 10);     to = @(27.4, 26, 12.6);  tex = 'copper' },
        @{ from = @(5.2, 25.4, 4);    to = @(27, 27.4, 8.6);   tex = 'copper' }, # 顶部总管（压到筒体面）
        @{ from = @(22.6, 8, 22.4);   to = @(28, 16, 28.6);    tex = 'dark' },   # 电路控制盒（东面，伸进筒身 0.9）
        @{ from = @(24.2, 8.6, 24.6); to = @(24.7, 15.4, 28);  tex = 'board' },  # 电路板面板
        @{ from = @(24.8, 10, 26);    to = @(25.3, 10.9, 26.9);tex = 'light' },  # 指示灯
        @{ from = @(8.5, 23, 7.6);    to = @(23.5, 24.2, 8.6); tex = 'wireB' },  # 蓝色线缆
        @{ from = @(8.5, 12, 7.6);    to = @(23.5, 13.2, 8.6); tex = 'wireY' }   # 黄色线缆
    )

    # 聚合釜：夹套釜体 + 顶封头 + 顶盖 + 两道夹套环 + 视镜 + 支腿 + 控制盒
    # 可动部件层：搅拌轮 y28.9..30.9；联轴节 y30.9..31.9（同轴不同高度，互不重叠）
    'polymerizer' = @(
        @{ from = @(6, 2.4, 6);       to = @(26, 5, 26);       tex = 'iron' },   # 下封头（压到 y2.4，与底部连接件重叠）
        @{ from = @(5.5, 4.6, 5.5);   to = @(26.5, 26, 26.5);  tex = 'iron' },   # 釜体
        @{ from = @(7.5, 25.6, 7.5);  to = @(24.5, 28.6, 24.5);tex = 'copper' }, # 顶封头
        @{ from = @(5, 9, 5);         to = @(27, 11, 27);      tex = 'copper' }, # 夹套环 1
        @{ from = @(5, 19, 5);        to = @(27, 21, 27);      tex = 'copper' }, # 夹套环 2
        @{ from = @(13.5, 12, 4.4);   to = @(18.5, 19, 5.8);   tex = 'glass' },  # 视镜
        @{ from = @(4.4, 3, 14);      to = @(5.8, 24, 18);     tex = 'copper' }, # 侧加强筋 ×4
        @{ from = @(26.2, 3, 14);     to = @(27.6, 24, 18);    tex = 'copper' },
        @{ from = @(14, 3, 4.4);      to = @(18, 24, 5.8);     tex = 'copper' },
        @{ from = @(14, 3, 26.2);     to = @(18, 24, 27.6);    tex = 'copper' },
        @{ from = @(4.6, 5, 4.6);     to = @(7, 5.6, 7);       tex = 'dark' },   # 支脚 ×4
        @{ from = @(25, 5, 4.6);      to = @(27.4, 5.6, 7);    tex = 'dark' },
        @{ from = @(4.6, 5, 25);      to = @(7, 5.6, 27.4);    tex = 'dark' },
        @{ from = @(25, 5, 25);       to = @(27.4, 5.6, 27.4); tex = 'dark' },
        @{ from = @(25, 12, 6);       to = @(28.4, 20, 12);    tex = 'dark' },   # 电路控制盒
        @{ from = @(24.6, 12.6, 6.6); to = @(25.1, 19.4, 11.4);tex = 'board' },  # 电路板面板
        @{ from = @(24.4, 14, 7.6);   to = @(24.9, 14.9, 8.5); tex = 'light' },  # 指示灯
        @{ from = @(8, 22.6, 5.4);    to = @(24, 23.8, 6.4);   tex = 'wireB' }   # 蓝色线缆
    )
}
# ── 可动部件（每台 2 个；旋转时只占自己那一层，与静止件留净空） ─────────────────────
$parts = [ordered]@{

    # 分馏塔 · 顶置冷凝风扇（Y，3°/t；y31.5..32，支梁在 31.3 之下）
    'distillation_tower_fan' = @(
        @{ from = @(15.2, 31.5, 15.2); to = @(16.8, 32, 16.8); tex = 'iron' },   # 轮毂
        @{ from = @(11, 31.5, 15.2);   to = @(21, 31.9, 16.8); tex = 'copper' }, # 叶片（横）
        @{ from = @(15.2, 31.5, 11);   to = @(16.8, 31.9, 21); tex = 'copper' }  # 叶片（纵）
    )
    # 分馏塔 · 底部卸料阀（Y，5°/t；y0.3..1.7，裙座从 1.9 起）
    'distillation_tower_valve' = @(
        @{ from = @(15.2, 0.3, 15.2);  to = @(16.8, 1.7, 16.8); tex = 'iron' },  # 阀轴
        @{ from = @(13, 0.7, 15.2);    to = @(19, 1.3, 16.8);   tex = 'copper' },# 星轮（横）
        @{ from = @(15.2, 0.7, 13);    to = @(16.8, 1.3, 19);   tex = 'copper' } # 星轮（纵）
    )

    # 裂解炉 · 烟囱抽风（Y，6°/t；y31.4..32）
    'cracking_furnace_fan' = @(
        @{ from = @(15.2, 31.4, 15.2); to = @(16.8, 32, 16.8); tex = 'iron' },   # 轮毂
        @{ from = @(13, 31.4, 15.2);   to = @(19, 31.8, 16.8); tex = 'copper' }, # 叶片（横）
        @{ from = @(15.2, 31.4, 13);   to = @(16.8, 31.8, 19); tex = 'copper' }  # 叶片（纵）
    )
    # 裂解炉 · 炉门手轮（Z，2°/t；z0.6..1.9，炉门钢板从 2.1 起；绕 Z 旋转 → z 不变）
    'cracking_furnace_handwheel' = @(
        @{ from = @(15.2, 15.2, 0.6);  to = @(16.8, 16.8, 1.9); tex = 'dark' },   # 轮轴
        @{ from = @(12.6, 15.2, 0.8);  to = @(19.4, 16.8, 1.4); tex = 'dark' },   # 轮辐（横）
        @{ from = @(15.2, 12.6, 0.8);  to = @(16.8, 19.4, 1.4); tex = 'dark' }    # 轮辐（纵）
    )

    # 重整器 · 顶置搅拌轮（Y，2.5°/t；y28.9..30.9，封头 28.6 收口）
    'reformer_agitator' = @(
        @{ from = @(15.2, 28.9, 15.2); to = @(16.8, 30.9, 16.8); tex = 'iron' }, # 轮毂
        @{ from = @(11, 28.9, 15.2);   to = @(21, 29.6, 16.8);   tex = 'copper' },# 轮辐（横）
        @{ from = @(15.2, 28.9, 11);   to = @(16.8, 29.6, 21);   tex = 'copper' },# 轮辐（纵）
        @{ from = @(12.4, 29.6, 15);   to = @(19.6, 30.4, 17);   tex = 'copper' },# 上层小桨（横）
        @{ from = @(15, 29.6, 12.4);   to = @(17, 30.4, 19.6);   tex = 'copper' } # 上层小桨（纵）
    )
    # 重整器 · 底部阀门（Y，5°/t；y0.3..1.7）
    'reformer_valve' = @(
        @{ from = @(15.2, 0.3, 15.2);  to = @(16.8, 1.7, 16.8); tex = 'iron' },  # 阀轴
        @{ from = @(13, 0.7, 15.2);    to = @(19, 1.3, 16.8);   tex = 'copper' },# 星轮（横）
        @{ from = @(15.2, 0.7, 13);    to = @(16.8, 1.3, 19);   tex = 'copper' } # 星轮（纵）
    )

    # 聚合釜 · 顶置搅拌轮（Y，2.5°/t；y28.9..30.9，封头 28.6 收口）
    'polymerizer_agitator' = @(
        @{ from = @(14.6, 28.9, 14.6); to = @(17.4, 30.9, 17.4); tex = 'iron' }, # 轮毂
        @{ from = @(10.6, 28.9, 15.2); to = @(21.4, 29.6, 16.8); tex = 'copper' },# 轮辐（横）
        @{ from = @(15.2, 28.9, 10.6); to = @(16.8, 29.6, 21.4); tex = 'copper' } # 轮辐（纵）
    )
    # 聚合釜 · 顶部联轴节（Y，6°/t；y30.9..31.9，与搅拌轮同轴不同高度）
    'polymerizer_coupling' = @(
        @{ from = @(14.4, 30.9, 14.4); to = @(17.6, 31.9, 17.6); tex = 'dark' },  # 联轴毂
        @{ from = @(15.2, 30.9, 15.2); to = @(16.8, 31.2, 16.8); tex = 'gold' },  # 触点
        @{ from = @(12.8, 31.1, 15.2); to = @(19.2, 31.7, 16.8); tex = 'copper' },# 飞轮叶（横）
        @{ from = @(15.2, 31.1, 12.8); to = @(16.8, 31.7, 19.2); tex = 'copper' } # 飞轮叶（纵）
    )
}

# ══════════════════════════════════════════════════════════════════════════════
# 精修（2026-10-14）：机体加细节 + 每台再加 1 个可动部件（工作动画）
#   · 只「追加」元素：$frame 与上面的原有构件**一律不动**；
#   · 新元素必须与既有构件**重叠**（不浮空）——由 tools/check_petro_models.ps1 的 float 项把关；
#   · 新可动部件占用的「轴向薄层」在下面逐台标注，静止件不得侵入（sweep 项把关）。
#   · 每台的可动层（静止件必须避开）：
#       分馏塔   Z 层 z0.6..1.9（北面表盘，半径 ≤5.1）
#       裂解炉   Z 层 z0.6..1.9（西面表盘 x5..11 / 东面手轮 x12.6..19.4）
#       重整器   Z 层 z0.6..1.9（北面轮盘，半径 ≤3.5）
#       聚合釜   Y 层 y0.3..1.7（底部排污阀，半径 ≤3）＋ 顶部 y28.9..31.9
#       挤出机   Z 层 z0.6..1.9（北面表盘，半径 ≤5.1）＋ 螺杆 X 向扫掠圆柱（YZ 半径 5.5）
#       硫化机   Y 层 y31.3..31.9（表针 + 后置风扇）＋ Y 层 y0.3..1.7（底阀）
# ══════════════════════════════════════════════════════════════════════════════
$extra = [ordered]@{

    # 分馏塔：底法兰 + 地脚螺栓 + 两道加强环 + 进料管法兰 + 顶部蒸汽管 + 压力表 + 侧换热器 + 排污阀 + 铭牌/警示/格栅/平台/电缆/工作灯
    'distillation_tower' = @(
        @{ from = @(5.4, 3.4, 5.4);   to = @(26.6, 4.6, 26.6);  tex = 'cuOx' },   # 塔底法兰（压住裙座顶）
        @{ from = @(6.6, 4.2, 6.6);   to = @(8.2, 5.2, 8.2);    tex = 'neth' },   # 地脚螺栓 ×4
        @{ from = @(23.8, 4.2, 6.6);  to = @(25.4, 5.2, 8.2);   tex = 'neth' },
        @{ from = @(6.6, 4.2, 23.8);  to = @(8.2, 5.2, 25.4);   tex = 'neth' },
        @{ from = @(23.8, 4.2, 23.8); to = @(25.4, 5.2, 25.4);  tex = 'neth' },
        @{ from = @(7.6, 11.6, 7.6);  to = @(24.4, 12.5, 24.4); tex = 'cutCu' },  # 塔身加强环 ×2
        @{ from = @(7.6, 18, 7.6);    to = @(24.4, 18.9, 24.4); tex = 'cutCu' },
        @{ from = @(23.8, 23.6, 13);  to = @(28.8, 26.2, 17);   tex = 'cuOx' },   # 侧进料管
        @{ from = @(23.2, 23.2, 12.4);to = @(29.4, 26.6, 17.6); tex = 'cuOx2' },  # 进料法兰
        @{ from = @(15.8, 29.2, 6);   to = @(20.2, 31.4, 11);   tex = 'cuOx' },   # 顶部蒸汽管（接冷凝器）
        # 北面压力表移到塔身正面中间（原位置偏西北角，会凸在框架空档里显得悬空）
        @{ from = @(10.2, 23.6, 7.2); to = @(14.4, 27.2, 8.6);  tex = 'dark' },   # 压力表壳（嵌入塔身 0.8）
        @{ from = @(10.6, 24.0, 7.0); to = @(14.0, 26.8, 7.6);  tex = 'quartz' }, # 白瓷表盘（贴壳前脸，可见）
        @{ from = @(26.2, 12, 13.6);  to = @(29.4, 20, 22.4);   tex = 'cutCu' },  # 侧换热器
        @{ from = @(25.4, 13.4, 15);  to = @(26.2, 18.6, 16.2); tex = 'cuOx' },   # 换热器翅片 ×2
        @{ from = @(25.4, 13.4, 19.4);to = @(26.2, 18.6, 20.6); tex = 'cuOx' },
        @{ from = @(5.2, 6, 14);      to = @(9.4, 8.2, 18);     tex = 'cuOx2' },  # 西面排污阀（伸进塔身 1.4）
        @{ from = @(12, 25.6, 7.6);   to = @(18, 27, 8.4);      tex = 'chisel' }, # 铭牌
        @{ from = @(8, 4.8, 7.6);     to = @(24, 5.8, 8.4);     tex = 'orange' }, # 警示条
        @{ from = @(9, 29.8, 9.4);    to = @(23, 30.8, 10.8);   tex = 'grate' },  # 通风格栅
        @{ from = @(22.6, 16.2, 14.2);to = @(27.2, 17.6, 20.8); tex = 'cutCu' },  # 检修平台（接侧梯）
        @{ from = @(26.8, 15.4, 5.2); to = @(28.2, 18.6, 11);   tex = 'hose' },   # 电缆导管（搭在控制盒上）
        @{ from = @(25.2, 15.4, 4.4); to = @(26.4, 16.6, 5.4);  tex = 'lamp' },   # 工作灯（贴在控制盒前脸）
        # 表架：表杆从塔身正面伸出来、表座贴在杆前端 —— 否则前置压力表悬在机体前方 7px 的空中
        @{ from = @(15.2, 15.2, 2.15);to = @(16.8, 16.8, 9.0);  tex = 'cuOx' },   # 压力表杆（嵌进塔身 1.0）
        @{ from = @(13.4, 13.4, 2.15);to = @(18.6, 18.6, 2.9);  tex = 'dark' },   # 压力表座
        @{ from = @(13.6, 1.8, 13.6); to = @(18.4, 3.2, 18.4);  tex = 'cuOx2' }   # 底部卸料阀座（压住裙座）
    )

    # 裂解炉：炉门砖带/铰链/观火口/铭牌/警示/灯 + 接线盒 + 烟囱法兰 + 侧梯 + 排渣口 + 隔热白带 + 侧管
    #   前脸细节 z 一律从 2.06 起：手轮 / 表盘的扫掠层是 z0.6..1.9，贴太近会被判定为撞到可动件
    'cracking_furnace' = @(
        @{ from = @(6.4, 3.4, 2.06);  to = @(24.6, 5.2, 2.56);   tex = 'blkBrk' }, # 炉衬砖带（下）
        @{ from = @(6.4, 20.4, 2.06); to = @(24.6, 22.2, 2.56);  tex = 'blkBrk' }, # 炉衬砖带（上）
        @{ from = @(6.2, 7.6, 2.06);  to = @(7.6, 10.4, 2.66);   tex = 'neth' },   # 门铰链 ×2
        @{ from = @(6.2, 18.4, 2.06); to = @(7.6, 21.2, 2.66);   tex = 'neth' },
        @{ from = @(10.2, 13.2, 2.06);to = @(15.8, 17.8, 2.26);  tex = 'dark' },   # 观火口底板
        @{ from = @(10.8, 13.8, 2.26);to = @(15.2, 17.2, 2.56);  tex = 'glowst' }, # 观火口辉光（贴在底板外）
        @{ from = @(16.6, 21.4, 2.06);to = @(22.6, 23, 2.56);    tex = 'chisel' }, # 铭牌
        @{ from = @(7.4, 16.4, 2.06); to = @(9.6, 19.4, 2.56);   tex = 'orange' }, # 警示条 ×2
        @{ from = @(20.4, 16.4, 2.06);to = @(22.6, 19.4, 2.56);  tex = 'orange' },
        @{ from = @(6.4, 10.4, 2.06); to = @(8.2, 12.0, 2.72);  tex = 'lamp' },   # 工作灯 ×2（嵌进门板 0.6）
        @{ from = @(22.6, 10.4, 2.06);to = @(24.4, 12.0, 2.72);  tex = 'lamp' },
        @{ from = @(2.6, 9.6, 2.6);   to = @(5.4, 13.4, 7.4);   tex = 'dark' },   # 加热线圈接线盒
        @{ from = @(2.6, 10.2, 3.2);  to = @(3.1, 12.8, 6.8);   tex = 'board' },  # 接线盒面板
        @{ from = @(2.4, 11, 4.2);    to = @(2.9, 11.9, 5.1);   tex = 'light' },
        @{ from = @(11.2, 26.6, 11.2);to = @(20.8, 27.8, 20.8); tex = 'cuOx' },   # 烟囱法兰
        @{ from = @(27.6, 4.4, 13.6); to = @(30.4, 22, 16.4);   tex = 'dark' },   # 侧梯
        @{ from = @(12.6, 1.9, 26.4); to = @(19.4, 3.8, 29.6);  tex = 'neth' },   # 排渣口
        @{ from = @(3.6, 23.6, 3.6);  to = @(28.4, 24.8, 28.4); tex = 'white' },  # 隔热白带
        @{ from = @(2.6, 9.6, 18.6);  to = @(5.6, 24.4, 21.2);  tex = 'cuOx2' },  # 西侧管道
        @{ from = @(2.6, 22.4, 18.6); to = @(6.6, 24.6, 22.6);  tex = 'cuOx2' }   # 管道弯头
    )

    # 重整器：加强环 ×2 + 侧平台/栏杆 + 侧换热器 + 排污阀 + 压力表 + 侧管阀门 ×2 + 铭牌/警示/电缆/灯
    'reformer' = @(
        @{ from = @(8.2, 13.2, 8.2);  to = @(23.8, 14.1, 23.8); tex = 'cutCu' },  # 筒体加强环 ×2
        @{ from = @(8.2, 22, 8.2);    to = @(23.8, 22.9, 23.8); tex = 'cutCu' },
        @{ from = @(24.2, 15.6, 10);  to = @(27.8, 17, 20.4);   tex = 'cutCu' },  # 侧平台
        @{ from = @(24.2, 16.6, 19.2);to = @(27.8, 21, 20.4);   tex = 'grate' },  # 平台栏杆
        @{ from = @(2.6, 11.6, 14);   to = @(6.2, 19.6, 20);    tex = 'cutCu' },  # 侧换热器
        @{ from = @(2.6, 14.4, 16);   to = @(8.8, 17.2, 18.6);  tex = 'cuOx' },   # 换热器连接管
        @{ from = @(5.2, 5, 14);      to = @(9.6, 7.6, 18);     tex = 'cuOx2' },  # 排污阀（伸进筒身 1.1）
        @{ from = @(19, 23.6, 7.9);   to = @(22.4, 26.6, 9.2);  tex = 'dark' },   # 北面压力表壳（嵌入筒身 0.7）
        @{ from = @(19.4, 24.2, 7.4); to = @(22.0, 26.2, 8.4);  tex = 'quartz' }, # 白瓷表盘（贴壳前脸，可见）
        @{ from = @(24.6, 8, 10.2);   to = @(27.4, 9.6, 12.4);  tex = 'cuOx' },   # 侧管阀门 ×2
        @{ from = @(4.6, 8, 10.2);    to = @(7.4, 9.6, 12.4);   tex = 'cuOx' },
        @{ from = @(11.6, 20.4, 8.4); to = @(17.6, 21.8, 9.0);  tex = 'chisel' }, # 铭牌
        @{ from = @(8.4, 19.4, 8.4);  to = @(23.6, 20.2, 8.9);  tex = 'orange' }, # 警示条
        @{ from = @(22.6, 12.4, 24.6);to = @(24.2, 19.6, 26.4); tex = 'hose' },   # 电缆导管
        @{ from = @(24.2, 14.6, 21.6);to = @(25.6, 16.0, 22.8); tex = 'lamp' },   # 工作灯（贴在控制盒前脸）
        # 手轮架：轮杆从筒身正面伸出来、轮座贴在杆前端 —— 否则前置手轮悬在机体前方 6.6px 的空中
        @{ from = @(15.2, 15.2, 2.15);to = @(16.8, 16.8, 9.2);  tex = 'cuOx' },   # 手轮杆（嵌进筒身 0.7）
        @{ from = @(13.4, 13.4, 2.15);to = @(18.6, 18.6, 2.9);  tex = 'dark' }    # 手轮座
    )

    # 聚合釜：加强环 ×2 + 顶人孔/盖 + 第二个观察窗 + 侧出料阀 + 夹套接管 ×2 + 底盘格栅 + 铭牌/警示/灯 + 驱动电机
    'polymerizer' = @(
        @{ from = @(5.2, 14.4, 5.2);  to = @(26.8, 15.4, 26.8); tex = 'cutCu' },  # 釜体加强环 ×2
        @{ from = @(5.2, 23.2, 5.2);  to = @(26.8, 24.2, 26.8); tex = 'cutCu' },
        @{ from = @(12.6, 25.6, 6.2); to = @(19.4, 28.4, 9);    tex = 'blkCh' },  # 顶人孔座
        @{ from = @(13.4, 26, 5.2);   to = @(18.6, 28, 7.4);    tex = 'neth' },   # 人孔盖
        @{ from = @(13.4, 20.4, 5.2); to = @(18.6, 24.4, 6.2);  tex = 'dark' },   # 观察窗框
        @{ from = @(13.8, 20.8, 5.0); to = @(18.2, 24, 5.4);    tex = 'glass' },  # 观察窗玻璃
        @{ from = @(25.4, 6, 13);     to = @(29.2, 8.6, 17);    tex = 'cuOx' },   # 侧出料阀（伸进釜体 1.1）
        @{ from = @(4.2, 12, 8);      to = @(6.4, 14.6, 12);    tex = 'cuOx2' },  # 夹套接管 ×2
        @{ from = @(4.2, 19.6, 8);    to = @(6.4, 22.2, 12);    tex = 'cuOx2' },
        @{ from = @(6.4, 3.2, 6.4);   to = @(25.6, 4.2, 25.6);  tex = 'grate' },  # 底盘格栅
        @{ from = @(10, 22.4, 5.2);   to = @(16, 23.8, 6.0);    tex = 'chisel' }, # 铭牌
        @{ from = @(6, 7.4, 5.2);     to = @(26, 8.4, 6.0);     tex = 'orange' }, # 警示条
        @{ from = @(25.8, 16.2, 5.2); to = @(27.0, 17.4, 6.4);  tex = 'lamp' },   # 工作灯（贴在控制盒前脸）
        @{ from = @(12.6, 26.4, 19.4);to = @(19.4, 28.4, 24.6); tex = 'neth' },   # 顶部驱动电机
        @{ from = @(14.4, 1.8, 14.4); to = @(17.6, 3.0, 17.6);  tex = 'cuOx2' }   # 排污阀座（压住下封头，托住底阀）
    )

}
foreach ($id in $extra.Keys) { $bodies[$id] = @($bodies[$id]) + $extra[$id] }

# ── 煤化工线（C 线）机器：专属造型 ─────────────────────────────────────────────
# 气化炉：宽矮压力壳 + 阶梯锥底 + 穹顶 + 顶部煤锁斗 + 四向氧/蒸汽烧嘴 + 东侧旋风分离器 + 南侧排渣口。
#   可动层：顶 y30.1..31.4（料斗颈到 30.0）；底 y0.3..1.7（裙座从 1.9 起）；北面表针 z0.5..1.9。
$bodies['gasifier'] = @(
    # 裙座 / 底法兰 / 地脚螺栓（与底部铜管连接件重叠）
    @{ from = @(5, 1.9, 5);        to = @(27, 3.8, 27);      tex = 'iron' },
    @{ from = @(4.6, 3.4, 4.6);    to = @(27.4, 4.8, 27.4);  tex = 'cuOx' },
    @{ from = @(6, 4.2, 6);        to = @(8, 5.2, 8);        tex = 'neth' },
    @{ from = @(24, 4.2, 6);       to = @(26, 5.2, 8);       tex = 'neth' },
    @{ from = @(6, 4.2, 24);       to = @(8, 5.2, 26);       tex = 'neth' },
    @{ from = @(24, 4.2, 24);      to = @(26, 5.2, 26);      tex = 'neth' },
    # 下部阶梯锥段
    @{ from = @(9, 4.6, 9);        to = @(23, 6.4, 23);      tex = 'dark' },
    @{ from = @(8, 6.2, 8);        to = @(24, 8.0, 24);      tex = 'dark' },
    @{ from = @(7, 7.8, 7);        to = @(25, 9.6, 25);      tex = 'dark' },
    # 宽矮主筒体 + 耐火砖带 / 加强箍
    @{ from = @(6.5, 9.4, 6.5);    to = @(25.5, 20.4, 25.5); tex = 'iron' },
    @{ from = @(6.2, 10.4, 6.2);   to = @(25.8, 11.4, 25.8); tex = 'blkBrk' },
    @{ from = @(6.2, 14.6, 6.2);   to = @(25.8, 15.6, 25.8); tex = 'blkBrk' },
    @{ from = @(6.2, 11.4, 6.2);   to = @(25.8, 12.4, 25.8); tex = 'cutCu' },
    @{ from = @(6.2, 18.4, 6.2);   to = @(25.8, 19.4, 25.8); tex = 'cutCu' },
    # 上部阶梯穹顶
    @{ from = @(7.5, 20.2, 7.5);   to = @(24.5, 21.8, 24.5); tex = 'iron' },
    @{ from = @(9, 21.6, 9);       to = @(23, 23.2, 23);      tex = 'iron' },
    @{ from = @(11, 23.0, 11);     to = @(21, 24.6, 21);      tex = 'copper' },
    @{ from = @(13, 24.4, 13);     to = @(19, 26.0, 19);      tex = 'dark' },
    # 顶部煤锁斗 / 加煤料斗 / 颈法兰
    @{ from = @(10, 25.8, 10);     to = @(22, 27.4, 22);      tex = 'copper' },
    @{ from = @(11.5, 27.2, 11.5); to = @(20.5, 28.6, 20.5);  tex = 'copper' },
    @{ from = @(13.5, 28.4, 13.5); to = @(18.5, 30.0, 18.5);  tex = 'dark' },
    @{ from = @(12.8, 29.6, 12.8); to = @(19.2, 30.0, 19.2);  tex = 'cutCu' },
    # 四向氧气 / 蒸汽烧嘴（含辉光口）
    @{ from = @(10, 12.6, 4.2);    to = @(22, 14.4, 7.0);    tex = 'cuOx' },
    @{ from = @(11, 12.9, 3.8);    to = @(21, 14.1, 4.4);    tex = 'glow' },
    @{ from = @(10, 12.6, 25.0);   to = @(22, 14.4, 27.8);   tex = 'cuOx' },
    @{ from = @(11, 12.9, 27.6);   to = @(21, 14.1, 28.2);   tex = 'glow' },
    @{ from = @(25.0, 12.6, 10);   to = @(27.8, 14.4, 22);   tex = 'cuOx' },
    @{ from = @(27.6, 12.9, 11);   to = @(28.2, 14.1, 21);   tex = 'glow' },
    @{ from = @(4.2, 12.6, 10);    to = @(7.0, 14.4, 22);    tex = 'cuOx' },
    @{ from = @(3.8, 12.9, 11);    to = @(4.4, 14.1, 21);    tex = 'glow' },
    # 东侧合成气出口 + 旋风分离器
    @{ from = @(25.2, 16.5, 12);   to = @(29.6, 19.5, 20);   tex = 'copper' },
    @{ from = @(23.8, 19.0, 19.8); to = @(29.4, 25.6, 26.1); tex = 'iron' },
    @{ from = @(24.8, 17.8, 20.8); to = @(28.4, 19.2, 25.1); tex = 'dark' },
    @{ from = @(24.4, 25.4, 20.4); to = @(28.8, 26.2, 25.5); tex = 'copper' },
    @{ from = @(26.0, 26.0, 22.6); to = @(27.4, 29.4, 24.0); tex = 'copper' },
    # 底部渣池 / 南侧排渣口
    @{ from = @(5.5, 3.6, 5.5);    to = @(26.5, 5.0, 26.5);  tex = 'dark' },
    @{ from = @(12, 2.2, 25.4);    to = @(20, 4.6, 29.6);    tex = 'neth' },
    @{ from = @(13, 2.6, 29.2);    to = @(19, 4.2, 30.0);    tex = 'dark' },
    # 西侧控制箱 / 线缆 / 铭牌 / 警示条 / 工作灯
    @{ from = @(2.8, 6.5, 12);     to = @(6.8, 14.5, 19);    tex = 'dark' },
    @{ from = @(6.4, 7.1, 12.6);   to = @(6.9, 13.9, 18.4);  tex = 'board' },
    @{ from = @(6.5, 8.0, 13.2);   to = @(7.0, 8.9, 14.1);   tex = 'light' },
    @{ from = @(6.6, 14.0, 8.2);   to = @(7.8, 15.2, 12.4);  tex = 'hose' },
    @{ from = @(11, 18.6, 6.0);    to = @(17, 19.8, 6.4);    tex = 'chisel' },
    @{ from = @(8, 9.8, 6.3);      to = @(24, 10.6, 7.0);    tex = 'orange' },
    @{ from = @(20.2, 17.0, 6.2);  to = @(21.6, 18.0, 6.8);  tex = 'lamp' },
    # 北侧检修梯（托架接到筒体）
    @{ from = @(7.4, 5.0, 2.8);    to = @(9.0, 23.0, 4.0);   tex = 'dark' },
    @{ from = @(7.4, 6.0, 4.0);    to = @(9.0, 7.0, 6.8);    tex = 'copper' },
    @{ from = @(7.4, 18.0, 4.0);   to = @(9.0, 19.0, 6.8);   tex = 'copper' },
    # 北面压力表架（表针层 z0.5..1.9，架子从 2.15 起）
    @{ from = @(15.2, 15.2, 2.15); to = @(16.8, 16.8, 6.8);  tex = 'cuOx' },
    @{ from = @(13.4, 13.4, 2.15); to = @(18.6, 18.6, 2.9);  tex = 'dark' }
)

# 合成塔：高瘦白色保温壳（宽下段 + 腰部大法兰 + 窄上段）+ 顶部大法兰/瓶颈 + 东侧换热器 + 西侧冷激管 + 南侧视镜。
#   可动层：顶 y28.9..30.9（顶盖到 28.8）；底 y0.3..1.7（裙座从 1.9 起）；北面手轮 z0.5..1.9。
$bodies['synthesis_tower'] = @(
    # 裙座 / 底法兰 / 地脚螺栓
    @{ from = @(6, 1.9, 6);        to = @(26, 3.6, 26);      tex = 'iron' },
    @{ from = @(5.6, 3.4, 5.6);    to = @(26.4, 4.6, 26.4);  tex = 'cuOx' },
    @{ from = @(7, 4.2, 7);        to = @(9, 5.2, 9);        tex = 'neth' },
    @{ from = @(23, 4.2, 7);       to = @(25, 5.2, 9);       tex = 'neth' },
    @{ from = @(7, 4.2, 23);       to = @(9, 5.2, 25);       tex = 'neth' },
    @{ from = @(23, 4.2, 23);      to = @(25, 5.2, 25);      tex = 'neth' },
    # 下封头 + 宽下段 + 腰部大法兰 + 窄上段（瓶形轮廓，区别于分馏塔的直筒）
    @{ from = @(9, 4.4, 9);        to = @(23, 6.2, 23);      tex = 'dark' },
    @{ from = @(7.5, 6.0, 7.5);    to = @(24.5, 13.6, 24.5); tex = 'white' },
    @{ from = @(6.8, 13.4, 6.8);   to = @(25.2, 15.2, 25.2); tex = 'cutCu' },
    @{ from = @(9.0, 15.0, 9.0);   to = @(23.0, 23.5, 23.0); tex = 'white' },
    # 下段底法兰 + 法兰螺栓
    @{ from = @(6.8, 5.6, 6.8);    to = @(25.2, 7.0, 25.2);  tex = 'cutCu' },
    @{ from = @(8.0, 6.0, 6.4);    to = @(9.0, 6.8, 7.2);    tex = 'neth' },
    @{ from = @(11.5, 6.0, 6.4);   to = @(12.5, 6.8, 7.2);   tex = 'neth' },
    @{ from = @(19.5, 6.0, 6.4);   to = @(20.5, 6.8, 7.2);   tex = 'neth' },
    @{ from = @(23.0, 6.0, 6.4);   to = @(24.0, 6.8, 7.2);   tex = 'neth' },
    @{ from = @(8.0, 13.8, 6.4);   to = @(9.0, 14.8, 7.2);   tex = 'neth' },
    @{ from = @(23.0, 13.8, 6.4);  to = @(24.0, 14.8, 7.2);  tex = 'neth' },
    # 上封头 / 顶部大法兰 / 瓶颈 / 顶盖
    @{ from = @(9.5, 23.2, 9.5);   to = @(22.5, 25.0, 22.5); tex = 'dark' },
    @{ from = @(8.2, 24.6, 8.2);   to = @(23.8, 26.4, 23.8); tex = 'cutCu' },
    @{ from = @(13, 26.2, 13);     to = @(19, 27.6, 19);     tex = 'dark' },
    @{ from = @(12.2, 27.4, 12.2); to = @(19.8, 28.8, 19.8); tex = 'copper' },
    # 顶部法兰螺栓
    @{ from = @(9.0, 25.0, 7.8);   to = @(10.2, 26.0, 8.6);  tex = 'neth' },
    @{ from = @(21.8, 25.0, 7.8);  to = @(23.0, 26.0, 8.6);  tex = 'neth' },
    @{ from = @(9.0, 25.0, 23.4);  to = @(10.2, 26.0, 24.2); tex = 'neth' },
    @{ from = @(21.8, 25.0, 23.4); to = @(23.0, 26.0, 24.2); tex = 'neth' },
    # 东侧进出料换热器（接宽下段，另加窄上段连接管）
    @{ from = @(23.8, 7.0, 10);    to = @(28.6, 18.0, 22);   tex = 'cutCu' },
    @{ from = @(28.4, 8.0, 11);    to = @(29.2, 17.0, 13);   tex = 'cuOx' },
    @{ from = @(28.4, 8.0, 19);    to = @(29.2, 17.0, 21);   tex = 'cuOx' },
    @{ from = @(22.8, 16.0, 14);   to = @(24.4, 17.4, 18);   tex = 'copper' },
    @{ from = @(24.0, 17.8, 14);   to = @(28.0, 19.4, 18);   tex = 'copper' },
    @{ from = @(24.0, 5.8, 14);    to = @(28.0, 7.2, 18);    tex = 'copper' },
    # 西侧冷激 / 取样管（另加窄上段连接管）
    @{ from = @(4.2, 6.0, 12);     to = @(8.2, 22.0, 18);    tex = 'copper' },
    @{ from = @(3.4, 12.0, 13.2);  to = @(5.4, 14.4, 16.8);  tex = 'cuOx' },
    @{ from = @(8.0, 17.0, 14);    to = @(9.4, 18.4, 16);    tex = 'copper' },
    @{ from = @(5.0, 20.0, 18.0);  to = @(7.8, 21.4, 20.6);  tex = 'copper' },
    # 南侧视镜（窄上段，框 + 玻璃）
    @{ from = @(12, 16.0, 22.8);   to = @(20, 21.0, 23.6);   tex = 'dark' },
    @{ from = @(12.6, 16.6, 23.5); to = @(19.4, 20.4, 24.0); tex = 'glass' },
    # 控制箱 / 铭牌 / 警示条 / 工作灯 / 电缆
    @{ from = @(5.0, 17.0, 18.6);  to = @(9.5, 24.5, 23.5);  tex = 'dark' },
    @{ from = @(8.8, 17.6, 19.2);  to = @(9.3, 23.9, 22.9);  tex = 'board' },
    @{ from = @(9.0, 18.6, 20.2);  to = @(9.5, 19.5, 21.1);  tex = 'light' },
    @{ from = @(11, 21.4, 8.8);    to = @(17, 22.8, 9.2);    tex = 'chisel' },
    @{ from = @(8.4, 6.8, 7.4);    to = @(23.6, 7.6, 8.2);   tex = 'orange' },
    @{ from = @(18.0, 21.0, 8.8);  to = @(19.4, 22.4, 9.2);  tex = 'lamp' },
    @{ from = @(6.0, 23.0, 19.0);  to = @(8.2, 24.2, 21.2);  tex = 'hose' },
    # 西侧检修梯（两根托架分别接宽 / 窄段）
    @{ from = @(2.8, 5.4, 18.0);   to = @(4.4, 23.0, 19.6);  tex = 'dark' },
    @{ from = @(4.2, 6.0, 18.2);   to = @(8.2, 7.2, 19.4);   tex = 'copper' },
    @{ from = @(4.2, 18.0, 18.2);  to = @(9.2, 19.2, 19.4);  tex = 'copper' },
    # 北面手轮架（手轮层 z0.5..1.9，架子从 2.15 起，接到窄上段）
    @{ from = @(15.2, 15.2, 2.15); to = @(16.8, 16.8, 9.4);  tex = 'cuOx' },
    @{ from = @(13.4, 13.4, 2.15); to = @(18.6, 18.6, 2.9);  tex = 'dark' }
)

# ── 精修新增的可动部件（每台 1 个：摆针 / 轮盘 / 底阀 / 表针 / 风扇） ──────────────────
$extraParts = [ordered]@{

    # 分馏塔 · 北面压力表指针（Z，正弦摆动；z0.5..1.9，半径 ≤5.1）
    'distillation_tower_gauge' = @(
        @{ from = @(12.4, 12.4, 0.9);  to = @(19.6, 19.6, 1.9); tex = 'dark' },   # 表壳
        @{ from = @(12.8, 12.8, 0.6);  to = @(19.2, 19.2, 0.9); tex = 'quartz' }, # 白瓷盘
        @{ from = @(15.3, 14.2, 0.5);  to = @(16.7, 17.2, 0.7); tex = 'gold' }    # 指针
    )

    # 裂解炉 · 西面炉压表指针（Z，正弦摆动；z0.5..1.9，半径 ≤4.3）
    'cracking_furnace_dial' = @(
        @{ from = @(5.4, 13.4, 0.9);   to = @(10.6, 18.6, 1.9); tex = 'dark' },   # 表壳
        @{ from = @(5.8, 13.8, 0.6);   to = @(10.2, 18.2, 0.9); tex = 'quartz' }, # 白瓷盘
        @{ from = @(7.4, 14.4, 0.5);   to = @(8.6, 17.4, 0.7);  tex = 'gold' }    # 指针
    )

    # 重整器 · 北面手轮（Z，匀速自转；z1.0..1.6，半径 ≤3.5）
    'reformer_valve_wheel' = @(
        @{ from = @(14.8, 14.8, 0.9);  to = @(17.2, 17.2, 1.9); tex = 'neth' },   # 轮毂
        @{ from = @(12.6, 15.4, 1.0);  to = @(19.4, 16.6, 1.6); tex = 'cuOx' },   # 轮辐（横）
        @{ from = @(15.4, 12.6, 1.0);  to = @(16.6, 19.4, 1.6); tex = 'cuOx' }    # 轮辐（纵）
    )

    # 聚合釜 · 底部排污阀（Y，匀速自转；y0.3..1.7，半径 ≤3）
    'polymerizer_drain_valve' = @(
        @{ from = @(15.2, 0.3, 15.2);  to = @(16.8, 1.7, 16.8); tex = 'neth' },   # 阀轴
        @{ from = @(13, 0.7, 15.2);    to = @(19, 1.3, 16.8);   tex = 'cuOx' },   # 星轮（横）
        @{ from = @(15.2, 0.7, 13);    to = @(16.8, 1.3, 19);   tex = 'cuOx' }    # 星轮（纵）
    )

}
foreach ($id in $extraParts.Keys) { $parts[$id] = $extraParts[$id] }

# 煤化工线部件：专属几何（与 Java 端 PetroMachineKind 的轴 / 枢轴一致）
# 气化炉 · 顶部加煤螺旋（Y，3°/t；y30.1..31.4，料斗颈到 30.0）
$parts['gasifier_fan'] = @(
    @{ from = @(15.2, 30.1, 15.2); to = @(16.8, 31.4, 16.8); tex = 'iron' },   # 螺旋轴
    @{ from = @(11.5, 30.2, 15.2); to = @(20.5, 30.9, 16.8); tex = 'copper' }, # 螺旋叶（横）
    @{ from = @(15.2, 30.2, 11.5); to = @(16.8, 30.9, 20.5); tex = 'copper' }  # 螺旋叶（纵）
)
# 气化炉 · 底部排渣阀（Y，5°/t；y0.3..1.7，裙座从 1.9 起）
$parts['gasifier_valve'] = @(
    @{ from = @(15.2, 0.3, 15.2);  to = @(16.8, 1.7, 16.8); tex = 'neth' },    # 阀轴
    @{ from = @(13, 0.7, 15.2);    to = @(19, 1.3, 16.8);   tex = 'cuOx' },    # 星轮（横）
    @{ from = @(15.2, 0.7, 13);    to = @(16.8, 1.3, 19);   tex = 'cuOx' }     # 星轮（纵）
)
# 气化炉 · 北面压力表指针（Z，正弦摆动；z0.5..1.9，半径 ≤5.1）
$parts['gasifier_gauge'] = @(
    @{ from = @(12.4, 12.4, 0.9);  to = @(19.6, 19.6, 1.9); tex = 'dark' },    # 表壳
    @{ from = @(12.8, 12.8, 0.6);  to = @(19.2, 19.2, 0.9); tex = 'quartz' },  # 白瓷盘
    @{ from = @(15.3, 14.2, 0.5);  to = @(16.7, 17.2, 0.7); tex = 'gold' }     # 指针
)
# 合成塔 · 顶部搅拌/催化剂筐（Y，2.5°/t；y28.9..30.9，顶盖到 28.8）
$parts['synthesis_tower_agitator'] = @(
    @{ from = @(15.2, 28.9, 15.2); to = @(16.8, 30.9, 16.8); tex = 'iron' },   # 轮毂
    @{ from = @(11, 28.9, 15.2);   to = @(21, 29.6, 16.8);   tex = 'copper' }, # 轮辐（横）
    @{ from = @(15.2, 28.9, 11);   to = @(16.8, 29.6, 21);   tex = 'copper' }, # 轮辐（纵）
    @{ from = @(12.4, 29.6, 15);   to = @(19.6, 30.4, 17);   tex = 'copper' }, # 上层小桨（横）
    @{ from = @(15, 29.6, 12.4);   to = @(17, 30.4, 19.6);   tex = 'copper' }  # 上层小桨（纵）
)
# 合成塔 · 底部出料阀（Y，5°/t；y0.3..1.7，裙座从 1.9 起）
$parts['synthesis_tower_valve'] = @(
    @{ from = @(15.2, 0.3, 15.2);  to = @(16.8, 1.7, 16.8); tex = 'neth' },    # 阀轴
    @{ from = @(13, 0.7, 15.2);    to = @(19, 1.3, 16.8);   tex = 'cuOx' },    # 星轮（横）
    @{ from = @(15.2, 0.7, 13);    to = @(16.8, 1.3, 19);   tex = 'cuOx' }     # 星轮（纵）
)
# 合成塔 · 北面手轮（Z，匀速自转；z0.5..1.9，半径 ≤3.5）
$parts['synthesis_tower_gauge'] = @(
    @{ from = @(14.8, 14.8, 0.9);  to = @(17.2, 17.2, 1.9); tex = 'neth' },    # 轮毂
    @{ from = @(12.6, 15.4, 1.0);  to = @(19.4, 16.6, 1.6); tex = 'cuOx' },    # 轮辐（横）
    @{ from = @(15.4, 12.6, 1.0);  to = @(16.6, 19.4, 1.6); tex = 'cuOx' }     # 轮辐（纵）
)

# 底部铜管连接件（照燃烧引擎的管口样式）：每面底部 2 个，从铜框架伸入机体 ——
# 既做细节，又把机体与铜框架连成一体（否则机体会与框架脱开）。
# 值 = 从该面框架内沿伸入的深度（要够到机体，各机不同）。
$bottomReach = @{ distillation_tower = 6; cracking_furnace = 6; reformer = 6; polymerizer = 7; gasifier = 6; synthesis_tower = 6 }

function Get-BottomConnectors($reach) {
    $r = [double]$reach
    $far = 32.0 - $r
    return @(
        @{ from = @(7, 0, 0.5);    to = @(11, 3, $r);    tex = 'copper' },   # 北面 ×2
        @{ from = @(21, 0, 0.5);   to = @(25, 3, $r);    tex = 'copper' },
        @{ from = @(7, 0, $far);   to = @(11, 3, 31.5);  tex = 'copper' },   # 南面 ×2
        @{ from = @(21, 0, $far);  to = @(25, 3, 31.5);  tex = 'copper' },
        @{ from = @(0.5, 0, 7);    to = @($r, 3, 11);    tex = 'copper' },   # 西面 ×2
        @{ from = @(0.5, 0, 21);   to = @($r, 3, 25);    tex = 'copper' },
        @{ from = @($far, 0, 7);   to = @(31.5, 3, 11);  tex = 'copper' },   # 东面 ×2
        @{ from = @($far, 0, 21);  to = @(31.5, 3, 25);  tex = 'copper' }
    )
}

function Write-Model($id, $boxes) {
    $raw = @()
    foreach ($b in $boxes) { $raw += New-Element $b.from $b.to $b.tex }

    # 逐元素「微偏移」：第 k 个元素整体向内平移 k*0.00137（<0.07px，肉眼不可见），消除精确共面（z-fighting）。
    # 必须向内（负向）——模型元素坐标上限是 32，向外偏移会越界导致模型加载失败。
    $elements = @()
    $k = 0
    foreach ($e in $raw) {
        $d = -($k * 0.00137)
        $nf = @(); $nt = @()
        for ($i = 0; $i -lt 3; $i++) { $nf += [double]$e.from[$i] + $d; $nt += [double]$e.to[$i] + $d }
        $elements += [pscustomobject]@{ from = $nf; to = $nt; faces = $e.faces }
        $k++
    }

    $used = New-Object System.Collections.Generic.List[string]
    foreach ($b in $boxes) { if (-not $used.Contains($b.tex)) { $used.Add($b.tex) } }
    $texObj = New-Object System.Management.Automation.PSObject
    foreach ($alias in $used) { $texObj | Add-Member -MemberType NoteProperty -Name $alias -Value $T[$alias] }
    $texObj | Add-Member -MemberType NoteProperty -Name 'particle' -Value $T[$boxes[0].tex]

    # ambientocclusion = $false：模型是 2×2×2（大于 1 格），否则原版按「本方块那一格」算 AO，朝机体自己占地范围的面被压黑。
    $model = [pscustomobject]@{ parent = 'minecraft:block/block'; ambientocclusion = $false; textures = $texObj; elements = $elements }
    [System.IO.File]::WriteAllText((Join-Path $modelsDir "$id.json"), ($model | ConvertTo-Json -Depth 12), $UTF8)
}

function Write-SingleState($id) {
    # 2×2×2 超格模型不能用「朝向 y 旋转」（原版绕方块中心转，会把整机甩出去）→ 单变体、空属性名。
    $state = @{ variants = @{ '' = @{ model = "stellar_evolution:block/$id" } } }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "$id.json"), ($state | ConvertTo-Json -Depth 8), $UTF8)
}

# ── 接管口（照生化线口径，用户指定：接管口美术要在所有大机器上复用） ─────────────────────
# 机器占 2×2×2（主方块在最小角）。接点 = 四个水平侧面「最底一排」各 2 个（共 8）+ 底面 4 个，**按口独立**。
#   · 侧面接点在模型 y = 6..10、两个底格的格心（x/z = 6..10 或 22..26）。
#     **必须**放在 6..10 —— 铜管管臂的中心也在 6..10，否则管子接过来会上下错位一截。
#   · 每个侧面接点带一根「立式铜座」strap，从框架底梁（y 1..2）一直伸到管高 —— 否则会浮空。
#   · 底面 4 个接点没有铜座（机体就是结构），头部向下凸出 1px，避开机体底面 y=0 的共面闪烁。
# 每个口两态：open（**未接**：露出的 4×4 铜环 + 内凹深孔）/ capped（**已接**：被铜管封成一块铜垫）。
# 头部外侧面一律内缩 0.2px —— 贴着机器面会和铜管管臂的端面精确共面，插上管子就闪。
# 名字 = 方块状态属性名，与 Java 端 PetroMachineBlocks.PORTS 一一对应（两处必须一致）。
$ports = [ordered]@{
    port_north_a = @{ axis = 'z'; positive = $false; head = @(6,    6, 0.2,  10,  10, 2.2);  strap = @(6,    1, 0.2,  10,  7, 1.8) }
    port_north_b = @{ axis = 'z'; positive = $false; head = @(22,   6, 0.2,  26,  10, 2.2);  strap = @(22,   1, 0.2,  26,  7, 1.8) }
    port_east_a  = @{ axis = 'x'; positive = $true;  head = @(29.8, 6, 6,    31.8, 10, 10);   strap = @(30.2, 1, 6,    31.8, 7, 10) }
    port_east_b  = @{ axis = 'x'; positive = $true;  head = @(29.8, 6, 22,   31.8, 10, 26);   strap = @(30.2, 1, 22,   31.8, 7, 26) }
    port_south_a = @{ axis = 'z'; positive = $true;  head = @(6,    6, 29.8, 10,  10, 31.8);  strap = @(6,    1, 30.2, 10,  7, 31.8) }
    port_south_b = @{ axis = 'z'; positive = $true;  head = @(22,   6, 29.8, 26,  10, 31.8);  strap = @(22,   1, 30.2, 26,  7, 31.8) }
    port_west_a  = @{ axis = 'x'; positive = $false; head = @(0.2,  6, 6,    2.2,  10, 10);    strap = @(0.2,  1, 6,    1.8,  7, 10) }
    port_west_b  = @{ axis = 'x'; positive = $false; head = @(0.2,  6, 22,   2.2,  10, 26);    strap = @(0.2,  1, 22,   1.8,  7, 26) }
    # 底面 4 个接点：x/z 各**内缩 0.3**（3.4 格宽）—— 否则接点的侧面会和机体底面精确共面。
    port_down_a  = @{ axis = 'y'; positive = $false; head = @(6.3,  -1, 6.3,  9.7,  0.6, 9.7);  strap = @() }
    port_down_b  = @{ axis = 'y'; positive = $false; head = @(22.3, -1, 6.3,  25.7, 0.6, 9.7);  strap = @() }
    port_down_c  = @{ axis = 'y'; positive = $false; head = @(6.3,  -1, 22.3, 9.7,  0.6, 25.7); strap = @() }
    port_down_d  = @{ axis = 'y'; positive = $false; head = @(22.3, -1, 22.3, 25.7, 0.6, 25.7); strap = @() }
}

# 单个口的元素。$bx = @(x0, y0, z0, x1, y1, z1)（该口 4×4×2 的包围盒）；
# $axis = 厚度轴；$outwardPositive = 外侧是否在坐标大的一端。
# 【PS 5.1 坑】逗号（数组构造符）优先级**高于**加减：@($a, $b + 1, $c) 会被解析成 (($a, $b) + 1), $c，
# 减号更会直接报 "op_Subtraction"（Object[] 不支持 -）。所以下面每个算式**必须**带括号。
function New-PortElements($bx, $axis, $outwardPositive, $open) {
    $px0 = $bx[0]; $py0 = $bx[1]; $pz0 = $bx[2]
    $px1 = $bx[3]; $py1 = $bx[4]; $pz1 = $bx[5]
    if (-not $open) {
        return @{ from = @($px0, $py0, $pz0); to = @($px1, $py1, $pz1); tex = 'copper' }
    }
    $els = @()
    if ($axis -eq 'z') {
        $els += @{ from = @($px0, $py0, $pz0);             to = @($px1, ($py0 + 1), $pz1);           tex = 'copper' }
        $els += @{ from = @($px0, ($py1 - 1), $pz0);       to = @($px1, $py1, $pz1);               tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), $pz0);       to = @(($px0 + 1), ($py1 - 1), $pz1);   tex = 'copper' }
        $els += @{ from = @(($px1 - 1), ($py0 + 1), $pz0); to = @($px1, ($py1 - 1), $pz1);         tex = 'copper' }
        if ($outwardPositive) { $els += @{ from = @(($px0 + 1), ($py0 + 1), $pz0);     to = @(($px1 - 1), ($py1 - 1), ($pz1 - 1)); tex = 'dark' } }
        else                  { $els += @{ from = @(($px0 + 1), ($py0 + 1), ($pz0 + 1)); to = @(($px1 - 1), ($py1 - 1), $pz1);     tex = 'dark' } }
    } elseif ($axis -eq 'x') {
        $els += @{ from = @($px0, $py0, $pz0);             to = @($px1, ($py0 + 1), $pz1);           tex = 'copper' }
        $els += @{ from = @($px0, ($py1 - 1), $pz0);       to = @($px1, $py1, $pz1);               tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), $pz0);       to = @($px1, ($py1 - 1), ($pz0 + 1));   tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), ($pz1 - 1)); to = @($px1, ($py1 - 1), $pz1);           tex = 'copper' }
        if ($outwardPositive) { $els += @{ from = @($px0, ($py0 + 1), ($pz0 + 1));         to = @(($px1 - 1), ($py1 - 1), ($pz1 - 1)); tex = 'dark' } }
        else                  { $els += @{ from = @(($px0 + 1), ($py0 + 1), ($pz0 + 1));     to = @($px1, ($py1 - 1), ($pz1 - 1));     tex = 'dark' } }
    } else {
        $els += @{ from = @($px0, $py0, $pz0);             to = @(($px0 + 1), $py1, $pz1);           tex = 'copper' }
        $els += @{ from = @(($px1 - 1), $py0, $pz0);       to = @($px1, $py1, $pz1);               tex = 'copper' }
        $els += @{ from = @(($px0 + 1), $py0, $pz0);       to = @(($px1 - 1), $py1, ($pz0 + 1));   tex = 'copper' }
        $els += @{ from = @(($px0 + 1), $py0, ($pz1 - 1)); to = @(($px1 - 1), $py1, $pz1);           tex = 'copper' }
        if ($outwardPositive) { $els += @{ from = @(($px0 + 1), $py0, ($pz0 + 1));         to = @(($px1 - 1), ($py1 - 1), ($pz1 - 1)); tex = 'dark' } }
        else                  { $els += @{ from = @(($px0 + 1), ($py0 + 1), ($pz0 + 1));     to = @(($px1 - 1), $py1, ($pz1 - 1));     tex = 'dark' } }
    }
    return $els
}

# 出接口模型：每个接点两态 —— open（未接：铜环 + 深孔）/ capped（已接：一块铜垫），都带上铜座。
# 外加 1 个「含接口」的物品模型（用默认的未接态，和刚放下时一致）。
function Write-PortModels($id, $bodyBoxes) {
    $openAll = @()
    foreach ($name in $ports.Keys) {
        $spec = $ports[$name]
        $head = $spec.head
        $strapEls = @()
        if ($spec.strap.Count -gt 0) {
            $strapEls = @(@{ from = @($spec.strap[0], $spec.strap[1], $spec.strap[2]); to = @($spec.strap[3], $spec.strap[4], $spec.strap[5]); tex = 'copper' })
        }
        $headBox = @($head[0], $head[1], $head[2], $head[3], $head[4], $head[5])
        $openEls = $strapEls + (New-PortElements $headBox $spec.axis $spec.positive $true)
        $openAll += $openEls
        Write-Model "${id}_${name}_open" $openEls
        $cappedEls = $strapEls + (New-PortElements $headBox $spec.axis $spec.positive $false)
        Write-Model "${id}_${name}_capped" $cappedEls
    }
    # 物品模型：机体 + 全部接口（默认未接态）
    Write-Model "${id}_item" ($bodyBoxes + $openAll)
}

# 接管口机器的 blockstate：multipart = 机体常驻 + 每个口按状态叠模型。
# 映射是**反的**：未接（false）露开口，已接（true）被封成铜垫。
function Write-PortState($id) {
    $parts = New-Object System.Collections.Generic.List[object]
    $parts.Add([pscustomobject]@{ apply = [pscustomobject]@{ model = "stellar_evolution:block/$id" } })
    foreach ($name in $ports.Keys) {
        foreach ($state in @('false', 'true')) {
            $suffix = if ($state -eq 'true') { 'capped' } else { 'open' }
            $when = New-Object System.Management.Automation.PSObject
            $when | Add-Member -MemberType NoteProperty -Name $name -Value $state
            $parts.Add([pscustomobject]@{
                when  = $when
                apply = [pscustomobject]@{ model = "stellar_evolution:block/${id}_${name}_$suffix" }
            })
        }
    }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "$id.json"),
        ([pscustomobject]@{ multipart = $parts.ToArray() } | ConvertTo-Json -Depth 10), $UTF8)
}

foreach ($id in $bodies.Keys) {
    $boxes = @($frame) + $bodies[$id] + (Get-BottomConnectors $bottomReach[$id])
    Write-Model $id $boxes
    Write-PortModels $id $boxes
    Write-PortState $id
    Write-Host ("  {0}  机体 {1} 块 + 接管口模型 25 个" -f $id, $boxes.Count)

    $item = [pscustomobject]@{ model = [pscustomobject]@{ type = 'minecraft:model'; model = "stellar_evolution:block/${id}_item" } }
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
    [System.IO.File]::WriteAllText((Join-Path $lootDir "$id.json"), ($loot | ConvertTo-Json -Depth 10), $UTF8)
}

foreach ($partId in $parts.Keys) {
    Write-Model $partId $parts[$partId]
    Write-SingleState $partId
    Write-Host ("  {0}  部件 {1} 块" -f $partId, $parts[$partId].Count)
}

# 机器「填充格」隐藏方块：共用一个空模型；每种机器一个方块（名字跟机器一致，Jade 才能正确识别）
$shellModel = [pscustomobject]@{ parent = 'minecraft:block/block'; textures = [pscustomobject]@{ particle = $T['iron'] } }
[System.IO.File]::WriteAllText((Join-Path $modelsDir 'petro_machine_shell.json'), ($shellModel | ConvertTo-Json -Depth 6), $UTF8)
foreach ($id in $bodies.Keys) {
    $shellState = @{ variants = @{ '' = @{ model = 'stellar_evolution:block/petro_machine_shell' } } }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "${id}_shell.json"), ($shellState | ConvertTo-Json -Depth 6), $UTF8)
    Write-Host ("  {0}_shell  空模型 + 单变体" -f $id)
}

Write-Host ("完成：{0} 台机器 + {1} 个可动部件" -f $bodies.Count, $parts.Count)

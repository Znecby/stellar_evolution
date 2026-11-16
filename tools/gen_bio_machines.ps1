# gen_bio_machines.ps1 —— 生化线单方块机器：机体（2×2×2）+ 可动部件（多部件动画）。
#
# 本轮目标：
#   ① 细节更密：管道 / 彩色线缆 / 电路控制盒 / 仪表 / 阀门 / 法兰 / 铭牌 / 加强筋；
#   ② 每台机器 2 个可动部件（同轴双级或小摆角指针），像真在运转；
#   ③ 不穿模：**任何旋转件与静止件留 ≥0.4 间隙**，旋转扫掠半径都在净空内；
#   ④ 不闪烁：**所有堆叠面一律互相重叠 0.4–1px**（共面 = z-fighting），
#      配套 `tools/check_coplanar.ps1` 逐对检测共面。
# 贴图全部复用原版（零新增贴图）。坐标 0..32（1 单位 = 1/16 格）。
#
# 可动部件（12 个 → 12 个隐藏载体方块）：
#   slurry_mixer_agitator / _anchor   调浆机：下层桨叶 + 上层锚式框，同轴不同速
#   fermenter_agitator / _coupling    发酵罐：顶置搅拌桨 + 更高位飞轮联轴节
#   decomposer_drum / _pulley          分解仪：卧式转鼓 + 同轴驱动皮带轮
#   sterilizer_handwheel / _needle    灭菌罐：舱门手轮 + 压力表指针（小幅摆动）
#   crystallizer_crystal / _valve     结晶机：顶部晶体簇 + 底部星型卸料阀
#   bioreactor_agitator / _sparger     生物反应器：贯通搅拌轴双层桨叶 + 底部气体分布器
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_bio_machines.ps1
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
    # 关键：**每个面都要写显式 UV**，且取**贴图中心区域**。
    # 不写 uv 时原版按元素尺寸自动裁剪贴图，元素一大（>16px）UV 就超出 16×16 贴图范围，
    # 采样会跑到图集里相邻的其它贴图上 —— 表现为大面渲染成别的方块（常常是暗色）的贴图；
    # 而若只取左上角一小块，细长零件（如 2×32 的框架）就只采到贴图边缘的暗色条 → 整机发暗。
    # 这里统一取以 (8,8) 为中心的那块区域，颜色最接近该贴图的「正常色」。
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
    copper = 'minecraft:block/copper_block'          # 铜：框架 / 夹套 / 管道 / 法兰 / 桨叶
    iron   = 'minecraft:block/iron_block'            # 铁：罐体 / 机架 / 筒体
    dark   = 'minecraft:block/polished_blackstone'   # 深色：电机 / 控制盒 / 手轮 / 转鼓
    bio    = 'minecraft:block/moss_block'            # 生物质 / 藻膜 / 培养液
    mud    = 'minecraft:block/mud'                   # 泥浆 / 淤浆（调浆机）
    cold   = 'minecraft:block/amethyst_block'        # 晶体
    light  = 'minecraft:block/redstone_block'        # 指示灯 / 仪表
    wireR  = 'minecraft:block/red_concrete'          # 彩色线缆 · 红
    wireY  = 'minecraft:block/yellow_concrete'       # 彩色线缆 · 黄
    wireB  = 'minecraft:block/light_blue_concrete'   # 彩色线缆 · 蓝
    board  = 'minecraft:block/green_concrete'        # 电路板基材
    glass  = 'minecraft:block/glass'                 # 视窗玻璃（机器方块走 cutout 渲染层）
    gold   = 'minecraft:block/gold_block'            # 电路走线 / 触点
}

# 2×2×2 铜框架：2px 粗；**内沿收到 2.5**（与机体重叠 0.5，避免共面闪烁）
$frame = @(
    @{ from = @(0, 0, 0);      to = @(2.5, 32, 2.5) },     # 角柱
    @{ from = @(29.5, 0, 0);   to = @(32, 32, 2.5) },
    @{ from = @(0, 0, 29.5);   to = @(2.5, 32, 32) },
    @{ from = @(29.5, 0, 29.5);to = @(32, 32, 32) },
    @{ from = @(2.5, 0, 0);    to = @(29.5, 2, 2.5) },     # 底梁（2px）
    @{ from = @(2.5, 0, 29.5); to = @(29.5, 2, 32) },
    @{ from = @(0, 0, 2.5);    to = @(2.5, 2, 29.5) },
    @{ from = @(29.5, 0, 2.5); to = @(32, 2, 29.5) },
    @{ from = @(2.5, 30, 0);   to = @(29.5, 32, 2.5) },    # 顶梁（2px）
    @{ from = @(2.5, 30, 29.5);to = @(29.5, 32, 32) },
    @{ from = @(0, 30, 2.5);   to = @(2.5, 32, 29.5) },
    @{ from = @(29.5, 30, 2.5);to = @(32, 32, 29.5) }
) | ForEach-Object { @{ from = $_.from; to = $_.to; tex = 'copper' } }

$bodies = [ordered]@{

    # 调浆机：敞开槽（薄壁）+ 浆液 + 顶横梁 + 电机 + 进/出料管（带法兰）+ 4 加强筋 +
    #         三色线缆 + 铜循环管 + 电路控制盒 + 2 指示灯
    'slurry_mixer' = @(
        @{ from = @(2, 1.5, 2);      to = @(30, 4, 30);      tex = 'iron' },    # 槽底（伸到 y1.5 与底梁重叠）
        @{ from = @(2, 3.5, 2);      to = @(30, 28, 4);      tex = 'iron' },    # 北壁（下沿 3.5 与底重叠）
        @{ from = @(2, 3.5, 28);     to = @(30, 28, 30);     tex = 'iron' },    # 南壁
        @{ from = @(2, 3.5, 4);      to = @(4, 28, 28);      tex = 'iron' },    # 西壁
        @{ from = @(28, 3.5, 4);     to = @(30, 28, 28);     tex = 'iron' },    # 东壁
        @{ from = @(5, 4, 5);        to = @(27, 13, 27);     tex = 'mud' },     # 泥浆主体（内缩一格，看出池壁）
        @{ from = @(4, 13, 4);       to = @(28, 14, 28);     tex = 'bio' },     # 藻膜浮渣层（贴住四壁）
        @{ from = @(5.5, 14, 5.5);   to = @(9, 15.4, 9);     tex = 'mud' },     # 浮渣块（槽角，避开桨叶扫掠半径）
        @{ from = @(23, 14, 23);     to = @(26.5, 15.4, 26.5);tex = 'mud' },    # 浮渣块
        @{ from = @(2, 27, 12);      to = @(30, 29, 20);     tex = 'copper' },  # 顶部横梁
        @{ from = @(11, 28.5, 12.5); to = @(21, 31.5, 19.5); tex = 'dark' },    # 搅拌电机
        @{ from = @(19.2, 29, 19.3); to = @(21, 30.6, 20.2); tex = 'light' },   # 电机铭牌
        @{ from = @(8, 27, 4);       to = @(11, 32, 8);      tex = 'copper' },  # 顶进料管
        @{ from = @(7.4, 30.5, 3.4); to = @(11.6, 32, 8.6);  tex = 'copper' }, # 进料管法兰
        @{ from = @(15, 4, 1.6);     to = @(17, 28, 2.4);    tex = 'copper' },  # 北加强筋
        @{ from = @(15, 4, 27.6);    to = @(17, 28, 28.4);   tex = 'copper' },  # 南加强筋
        @{ from = @(1.6, 4, 15);     to = @(2.4, 28, 17);    tex = 'copper' },  # 西加强筋
        @{ from = @(27.6, 4, 15);    to = @(28.4, 28, 17);   tex = 'copper' },  # 东加强筋
        @{ from = @(4, 22, 1.6);     to = @(26, 23.2, 2.6);  tex = 'wireR' },   # 红色线缆
        @{ from = @(4, 19.5, 1.6);   to = @(26, 20.7, 2.6);  tex = 'wireY' },   # 黄色线缆
        @{ from = @(4, 17, 1.6);     to = @(26, 18.2, 2.6);  tex = 'wireB' },   # 蓝色线缆
        @{ from = @(29.5, 4, 20);    to = @(31.2, 26, 23);   tex = 'copper' },  # 铜循环立管
        @{ from = @(28.8, 25, 19.4); to = @(31.8, 27, 23.6); tex = 'copper' },  # 立管顶部弯头
        @{ from = @(30, 14, 14);     to = @(32, 16, 18);     tex = 'copper' },  # 侧出料管
        @{ from = @(29.4, 13.4, 13.4);to = @(30.6, 16.6, 18.6); tex = 'copper' }, # 出料法兰
        @{ from = @(29.5, 20, 6);    to = @(31.6, 26, 12);   tex = 'dark' },    # 电路控制盒
        @{ from = @(29.2, 20.5, 6.5);to = @(29.7, 25.5, 11.5); tex = 'board' }, # 电路板面板
        @{ from = @(29.0, 22, 7.5);  to = @(29.4, 22.8, 8.3); tex = 'light' },  # 指示灯 1
        @{ from = @(29.0, 23.6, 7.5);to = @(29.4, 24.4, 8.3); tex = 'light' },  # 指示灯 2
        @{ from = @(29.2, 21.5, 9.6);to = @(29.8, 24.5, 10.2); tex = 'gold' }    # 电路走线
    )

    # 发酵罐：裙座 + 锥底 + 罐体 + 顶封头 + 顶盖 + 三道夹套 + 视镜 + 3 顶接口 + 侧取样阀 + 4 加强筋 +
    #         三色线缆 + 铜下料管 + 电路盒 + 铭牌
    'fermenter' = @(
        @{ from = @(6, 0, 6);        to = @(26, 2.5, 26);     tex = 'iron' },   # 裙座
        @{ from = @(9, 1.5, 9);      to = @(23, 6, 23);       tex = 'iron' },   # 锥底（与裙座重叠）
        @{ from = @(8, 5, 8);        to = @(24, 25, 24);      tex = 'iron' },   # 罐体（与锥底重叠）
        @{ from = @(9, 24, 9);       to = @(23, 28, 23);      tex = 'iron' },   # 顶封头
        @{ from = @(10, 27, 10);     to = @(22, 29.5, 22);    tex = 'copper' }, # 顶盖
        @{ from = @(7.25, 8, 7.25);  to = @(24.75, 10, 24.75);tex = 'copper' }, # 冷却夹套 1
        @{ from = @(7.25, 13, 7.25); to = @(24.75, 15, 24.75);tex = 'copper' }, # 冷却夹套 2
        @{ from = @(7.25, 18, 7.25); to = @(24.75, 20, 24.75);tex = 'copper' }, # 冷却夹套 3
        @{ from = @(14, 10.5, 6.6);  to = @(20, 14.5, 8.4);   tex = 'bio' },    # 视镜
        @{ from = @(9.5, 28.5, 14);  to = @(11.5, 31, 16);    tex = 'copper' }, # 顶接口 1
        @{ from = @(20.5, 28.5, 14); to = @(22.5, 31, 16);    tex = 'copper' }, # 顶接口 2
        @{ from = @(14, 28.5, 9.5);  to = @(16, 31, 11.5);    tex = 'copper' }, # 顶接口 3
        @{ from = @(24, 20, 14);     to = @(26.5, 22, 18);    tex = 'copper' }, # 侧取样阀（上移，避开扫掠）
        @{ from = @(7.8, 5, 14);     to = @(8.6, 25, 16);     tex = 'copper' }, # 北加强筋
        @{ from = @(23.4, 5, 14);    to = @(24.2, 25, 16);    tex = 'copper' }, # 南加强筋
        @{ from = @(14, 5, 7.8);     to = @(16, 25, 8.6);     tex = 'copper' }, # 西加强筋
        @{ from = @(14, 5, 23.4);    to = @(16, 25, 24.2);    tex = 'copper' }, # 东加强筋
        @{ from = @(8, 22, 7.4);     to = @(24, 23.2, 8.4);   tex = 'wireR' },  # 红色线缆
        @{ from = @(8, 20.2, 7.4);   to = @(24, 21.4, 8.4);   tex = 'wireY' },  # 黄色线缆
        @{ from = @(8, 11.5, 7.4);   to = @(24, 12.7, 8.4);   tex = 'wireB' },  # 蓝色线缆
        @{ from = @(24, 4, 19);      to = @(26.4, 14, 21.5);  tex = 'copper' }, # 铜下料立管
        @{ from = @(20, 26.4, 17);   to = @(23.4, 29.5, 21);  tex = 'copper' }, # 顶部出气管（接顶盖/顶封头）
        @{ from = @(24, 21, 6);      to = @(26.6, 26, 12);    tex = 'dark' },   # 电路控制盒
        @{ from = @(23.7, 21.5, 6.5);to = @(24.2, 25.5, 11.5);tex = 'board' },  # 电路板面板
        @{ from = @(23.5, 22.5, 8);  to = @(23.9, 23.3, 8.8); tex = 'light' },  # 指示灯
        @{ from = @(23.7, 23.5, 9.6);to = @(24.3, 24.5, 10.2);tex = 'gold' },   # 电路走线
        @{ from = @(14, 24.4, 7.4);  to = @(20, 26, 8.2);     tex = 'light' }   # 铭牌（贴在罐体上部）
    )

    # 分解仪：底板（承托 + 连框架）+ 机架腿/纵梁 + 两端轴承柱 + 电机立柱/电机 + 出料两路 +
    #         侧控制箱 + 三色线缆 + 铜管 + 触点排 + 铭牌（转鼓/皮带轮为可动部件）
    'decomposer' = @(
        @{ from = @(2.4, 0, 2.4);    to = @(29.6, 2, 29.6);  tex = 'iron' },    # 底板（与框架底梁重叠）
        @{ from = @(3, 1.5, 7);      to = @(5, 14, 9);       tex = 'iron' },    # 机架腿 ×4（落在底板上）
        @{ from = @(27, 1.5, 7);     to = @(29, 14, 9);      tex = 'iron' },
        @{ from = @(3, 1.5, 23);     to = @(5, 14, 25);      tex = 'iron' },
        @{ from = @(27, 1.5, 23);    to = @(29, 14, 25);     tex = 'iron' },
        @{ from = @(3, 12, 7);       to = @(29, 14, 9);      tex = 'iron' },    # 机架纵梁 ×2
        @{ from = @(3, 12, 23);      to = @(29, 14, 25);     tex = 'iron' },
        @{ from = @(6, 1.5, 13);     to = @(8.5, 22, 19);    tex = 'iron' },    # 西轴承柱（从底板升起，托转鼓端）
        @{ from = @(27, 1.5, 13);    to = @(29, 22, 19);     tex = 'iron' },    # 东轴承柱（托锥段端）
        @{ from = @(3, 1.5, 14);     to = @(6, 14, 18);      tex = 'iron' },    # 驱动电机立柱
        @{ from = @(3, 14, 14);      to = @(6, 22, 18);      tex = 'copper' },  # 驱动电机
        @{ from = @(1, 16, 15);      to = @(3, 20, 17);      tex = 'copper' },  # 电机端盖
        @{ from = @(12, 10.6, 25);   to = @(16, 12.6, 29);   tex = 'copper' },  # 液相出口（接南纵梁）
        @{ from = @(18, 10.6, 25);   to = @(22, 12.6, 29);   tex = 'iron' },    # 固相出口
        @{ from = @(28.4, 2, 10);    to = @(30.4, 10, 22);   tex = 'dark' },    # 侧控制箱（立于底板）
        @{ from = @(30.2, 2.5, 11);  to = @(30.7, 9.5, 21);  tex = 'board' },   # 电路板面板
        @{ from = @(29.9, 4, 13);    to = @(30.4, 4.8, 13.8);tex = 'light' },   # 指示灯
        @{ from = @(3, 12.6, 6.4);   to = @(29, 13.8, 7.6);  tex = 'wireR' },   # 红色线缆（贴北纵梁外侧）
        @{ from = @(3, 12.6, 24.4);  to = @(29, 13.8, 25.6); tex = 'wireY' },   # 黄色线缆（贴南纵梁外侧）
        @{ from = @(3, 1.6, 20);     to = @(29, 3, 22);      tex = 'wireB' },   # 蓝色线缆（贴底板面）
        @{ from = @(3, 14, 8.6);     to = @(6, 15.2, 14.2);  tex = 'copper' },  # 铜管（电机→北纵梁）
        @{ from = @(12, 14, 23.4);   to = @(20, 15.2, 24.6); tex = 'gold' },    # 排料触点排（接南纵梁）
        @{ from = @(7, 2, 2.6);      to = @(13, 3.6, 4.2);   tex = 'light' }    # 铭牌（贴底板北缘）
    )

    # 灭菌罐：细支腿 + 卧式筒体 + 两道加强环 + 封头法兰 + 圆形舱门 + 门心轴 + 压力表(侧上) + 安全阀(侧) +
    #         侧控制盒 + 三色线缆 + 铜管 + 铭牌（手轮/指针为可动部件）
    'sterilizer' = @(
        @{ from = @(7, 0, 9);       to = @(9, 5, 11);    tex = 'iron' },    # 支腿 ×4
        @{ from = @(23, 0, 9);      to = @(25, 5, 11);   tex = 'iron' },
        @{ from = @(7, 0, 21);      to = @(9, 5, 23);    tex = 'iron' },
        @{ from = @(23, 0, 21);     to = @(25, 5, 23);   tex = 'iron' },
        @{ from = @(7, 4.5, 7);     to = @(25, 27, 25);  tex = 'iron' },    # 卧式筒体
        @{ from = @(6.5, 10, 6.5);  to = @(25.5, 12, 25.5); tex = 'copper' }, # 加强环 1
        @{ from = @(6.5, 20, 6.5);  to = @(25.5, 22, 25.5); tex = 'copper' }, # 加强环 2
        @{ from = @(7, 7, 5.4);     to = @(25, 25, 7.4);  tex = 'copper' }, # 封头法兰
        @{ from = @(10, 10, 4.2);   to = @(22, 22, 6);    tex = 'copper' }, # 圆形舱门
        @{ from = @(15.4, 15.4, 4.0);to = @(16.6, 16.6, 4.6); tex = 'copper' }, # 门心轴（贴舱门）
        @{ from = @(12, 26.6, 13);  to = @(15.2, 29, 17.2); tex = 'dark' }, # 压力表盒（罐体顶部）
        @{ from = @(12.3, 28.6, 13.3);to = @(14.9, 29.2, 16.9); tex = 'light' }, # 表盘（指针下方）
        @{ from = @(24, 20, 13);    to = @(27, 24, 19);   tex = 'iron' },   # 安全阀（东侧）
        @{ from = @(24.6, 5, 10);   to = @(26.5, 20, 22); tex = 'dark' },   # 侧控制盒（贴筒体）
        @{ from = @(26.3, 6, 11);   to = @(26.8, 19, 21); tex = 'board' },  # 电路板面板
        @{ from = @(26.1, 8, 13);   to = @(26.6, 8.8, 13.8); tex = 'light' },# 指示灯
        @{ from = @(7, 26.6, 9);    to = @(25, 28, 11);   tex = 'wireR' },  # 红色线缆（贴筒体上沿）
        @{ from = @(7, 26.6, 14);   to = @(25, 28, 16);   tex = 'wireY' },  # 黄色线缆
        @{ from = @(7, 26.6, 19);   to = @(25, 28, 21);   tex = 'wireB' },  # 蓝色线缆
        @{ from = @(18, 26.6, 13);  to = @(20.4, 28, 17); tex = 'copper' }, # 铜接管（筒体顶部接口）
        @{ from = @(12, 12.6, 4.2); to = @(20, 14.2, 5);  tex = 'light' }   # 铭牌
    )

    # 结晶机：细支腿 + 上筒体 + 三级锥段 + 两道夹套 + 结晶视窗 + 顶部平台 + 侧进料 + 4 加强筋 +
    #         三色线缆 + 铜出料管 + 电路盒（晶体/卸料阀为可动部件）
    'crystallizer' = @(
        @{ from = @(5, 0, 5);       to = @(7, 10, 7);    tex = 'iron' },    # 支腿 ×4
        @{ from = @(25, 0, 5);      to = @(27, 10, 7);   tex = 'iron' },
        @{ from = @(5, 0, 25);      to = @(7, 10, 27);   tex = 'iron' },
        @{ from = @(25, 0, 25);     to = @(27, 10, 27);  tex = 'iron' },
        @{ from = @(5, 9, 5);       to = @(27, 25, 27);  tex = 'iron' },    # 上筒体
        @{ from = @(6, 5, 6);       to = @(26, 10, 26);  tex = 'iron' },    # 锥段 1
        @{ from = @(9, 3, 9);       to = @(23, 6, 23);   tex = 'iron' },    # 锥段 2
        @{ from = @(12.5, 2, 12.5); to = @(19.5, 4, 19.5); tex = 'iron' },  # 锥段 3（出料）
        @{ from = @(4.5, 12, 4.5);  to = @(27.5, 14, 27.5); tex = 'copper' }, # 冷却夹套 1
        @{ from = @(4.5, 18, 4.5);  to = @(27.5, 20, 27.5); tex = 'copper' }, # 冷却夹套 2
        @{ from = @(12, 14.5, 4.2); to = @(20, 18, 5.4); tex = 'cold' },    # 结晶视窗
        @{ from = @(5, 24, 5);      to = @(27, 26, 27);  tex = 'copper' },  # 顶部平台
        @{ from = @(27, 14, 13);    to = @(29.4, 18, 19);tex = 'copper' },  # 侧进料口
        @{ from = @(4.8, 9, 14);    to = @(5.6, 25, 16); tex = 'copper' },  # 北加强筋
        @{ from = @(26.4, 9, 14);   to = @(27.2, 25, 16);tex = 'copper' },  # 南加强筋
        @{ from = @(14, 9, 4.8);    to = @(16, 25, 5.6); tex = 'copper' },  # 西加强筋
        @{ from = @(14, 9, 26.4);   to = @(16, 25, 27.2);tex = 'copper' },  # 东加强筋
        @{ from = @(5, 21, 4.4);    to = @(27, 22.2, 5.4); tex = 'wireR' }, # 红色线缆
        @{ from = @(5, 7, 4.4);     to = @(27, 8.2, 5.4); tex = 'wireY' },  # 黄色线缆
        @{ from = @(5, 16, 26.6);   to = @(27, 17.2, 27.6);tex = 'wireB' }, # 蓝色线缆
        @{ from = @(13, 0, 20.4);   to = @(16, 4, 22.4);  tex = 'copper' }, # 铜出料管（锥底→外侧）
        @{ from = @(19, 4.2, 20.4); to = @(23, 5.4, 22.4);tex = 'gold' },   # 排料触点
        @{ from = @(27.2, 20, 6);   to = @(29.4, 25, 12); tex = 'dark' },   # 电路控制盒
        @{ from = @(26.9, 20.5, 6.5);to = @(27.4, 24.5, 11.5); tex = 'board' }, # 电路板面板
        @{ from = @(26.7, 22, 8);   to = @(27.1, 22.8, 8.8); tex = 'light' }, # 指示灯
        @{ from = @(5.2, 25.6, 5.2); to = @(8.2, 27.2, 8.2); tex = 'light' } # 铭牌（放平台角，避开晶体扫掠半径 9.9）
    )

    # 生物反应器（现代搅拌式·方案2 重做）：裙座 + 碟形底 + 底封头 + **笼式罐体**（4 立柱 + 3 道方形环箍，
    #   中间是空的 → 能看见里面的贯通搅拌轴与双层桨叶）+ 顶盘 + 顶部电机 + 4 补料口 + 排气口 + 顶部取样口 +
    #   北面双侧视镜 + 侧取样阀/夹套接管 + 底进气/排料 + 三色线缆 + 电路盒 + 铭牌
    'reactor' = @(
        @{ from = @(6, 0, 6);        to = @(26, 3.5, 26);      tex = 'iron' },   # 裙座（接框架底梁与底部连接件）
        @{ from = @(8, 2.5, 8);      to = @(24, 7, 24);        tex = 'iron' },   # 碟形底
        @{ from = @(6.8, 5.5, 6.8);  to = @(25.2, 8, 25.2);    tex = 'copper' }, # 底封头（联接底与立柱）
        @{ from = @(7, 7, 7);        to = @(9, 29, 9);         tex = 'iron' },   # 立柱 ×4
        @{ from = @(23, 7, 7);       to = @(25, 29, 9);        tex = 'iron' },
        @{ from = @(7, 7, 23);       to = @(9, 29, 25);        tex = 'iron' },
        @{ from = @(23, 7, 23);      to = @(25, 29, 25);       tex = 'iron' },
        @{ from = @(7, 10, 7);       to = @(25, 12, 9);        tex = 'copper' }, # 环箍 1（四根方杆，中间开）
        @{ from = @(7, 10, 23);      to = @(25, 12, 25);       tex = 'copper' },
        @{ from = @(7, 10, 9);       to = @(9, 12, 23);        tex = 'copper' },
        @{ from = @(23, 10, 9);      to = @(25, 12, 23);       tex = 'copper' },
        @{ from = @(8.5, 12.5, 7.2); to = @(23.5, 16, 8.4);    tex = 'glass' },  # 视窗玻璃（北，删掉中间横杆后空出的笼格）
        @{ from = @(8.5, 12.5, 23.6);to = @(23.5, 16, 24.8);   tex = 'glass' },  # 视窗玻璃（南）
        @{ from = @(7.2, 12.5, 8.5); to = @(8.4, 16, 23.5);    tex = 'glass' },  # 视窗玻璃（西）
        @{ from = @(23.6, 12.5, 8.5);to = @(24.8, 16, 23.5);   tex = 'glass' },  # 视窗玻璃（东）
        @{ from = @(7, 24, 7);       to = @(25, 26, 9);        tex = 'copper' }, # 环箍 3
        @{ from = @(7, 24, 23);      to = @(25, 26, 25);       tex = 'copper' },
        @{ from = @(7, 24, 9);       to = @(9, 26, 23);        tex = 'copper' },
        @{ from = @(23, 24, 9);      to = @(25, 26, 23);       tex = 'copper' },
        @{ from = @(8, 18, 6.4);     to = @(24, 23.5, 7.8);    tex = 'copper' }, # 配线铜板（北面，接立柱）
        @{ from = @(9.5, 19, 7.8);   to = @(22.5, 19.7, 8.3);  tex = 'gold' },   # 电路走线 ×3（连成一块电路板）
        @{ from = @(9.5, 20.6, 7.8); to = @(22.5, 21.3, 8.3);  tex = 'gold' },
        @{ from = @(9.5, 22.2, 7.8); to = @(22.5, 22.9, 8.3);  tex = 'gold' },
        @{ from = @(6.8, 25.5, 6.8); to = @(25.2, 28, 25.2);    tex = 'copper' }, # 顶盘
        @{ from = @(13, 27.5, 13);   to = @(19, 31.5, 19);     tex = 'dark' },   # 顶部电机
        @{ from = @(12.4, 29, 14);   to = @(13.2, 31.5, 18);   tex = 'copper' }, # 电机散热片 ×2
        @{ from = @(18.8, 29, 14);   to = @(19.6, 31.5, 18);   tex = 'copper' },
        @{ from = @(9.5, 28, 9.5);   to = @(12, 31, 12);       tex = 'copper' }, # 顶部补料口 ×4
        @{ from = @(20, 28, 9.5);    to = @(22.5, 31, 12);     tex = 'copper' },
        @{ from = @(9.5, 28, 20);    to = @(12, 31, 22.5);     tex = 'copper' },
        @{ from = @(20, 28, 20);     to = @(22.5, 31, 22.5);   tex = 'copper' },
        @{ from = @(20, 28, 15);     to = @(22.5, 31.2, 18);   tex = 'iron' },   # 排气口（避开搅拌轴）
        @{ from = @(9.5, 28, 14.5);  to = @(12, 30.6, 17);     tex = 'copper' }, # 顶部取样口
        @{ from = @(25, 22, 13.5);   to = @(27.5, 24.5, 18);     tex = 'copper' }, # 侧取样阀（挂在上部环箍上）
        @{ from = @(25, 10.5, 10);   to = @(27.5, 13, 13.5);   tex = 'copper' }, # 夹套接管（接环箍 1）
        @{ from = @(14, 1.5, 24.5);  to = @(18, 4, 27);        tex = 'copper' }, # 底部进气（接裙座）
        @{ from = @(13, 1.5, 8);     to = @(17, 4, 10.5);      tex = 'copper' }, # 底部排料（接裙座）
        @{ from = @(9.5, 23, 6.6);   to = @(11, 26.5, 7.6);    tex = 'wireR' },  # 彩色线缆 ×3（从配线板引到上部环箍）
        @{ from = @(11.5, 23, 6.6);  to = @(13, 26, 7.6);      tex = 'wireY' },
        @{ from = @(13.5, 23, 6.6);  to = @(15, 25.5, 7.6);    tex = 'wireB' },
        @{ from = @(8, 8.5, 6.6);    to = @(24, 9.7, 7.6);     tex = 'wireB' },  # 底部线缆
        @{ from = @(24.6, 11.5, 17); to = @(27, 16, 22.5);     tex = 'dark' },   # 电路控制盒（接环箍 1）
        @{ from = @(26.8, 12, 17.5); to = @(27.3, 15.5, 22);   tex = 'board' },  # 电路板面板
        @{ from = @(26.6, 13, 19);   to = @(27.1, 13.8, 19.8); tex = 'light' },  # 指示灯
        @{ from = @(9, 28, 24);      to = @(14, 30, 25.4);     tex = 'light' }   # 铭牌（顶盘南缘）
    )
}

# 可动部件（细件；坐标与机体同一 0..32 空间；绕各自枢轴旋转，与静止件留 ≥0.4 间隙）
$parts = [ordered]@{

    # 调浆机 · 下层桨叶（Y，4°/t）
    'slurry_mixer_agitator' = @(
        @{ from = @(15.3, 14, 15.3); to = @(16.7, 29, 16.7); tex = 'iron' },   # 立轴
        @{ from = @(9, 14.5, 15.2);  to = @(23, 16, 16.8);   tex = 'copper' }, # 桨叶（横）
        @{ from = @(15.2, 14.5, 9);  to = @(16.8, 16, 23);   tex = 'copper' }  # 桨叶（纵）
    )
    # 调浆机 · 上层锚式框（Y，3°/t；半径 10，槽内净空 12）
    'slurry_mixer_anchor' = @(
        @{ from = @(6, 17, 15.4);    to = @(7, 25, 16.6);    tex = 'copper' }, # 左立杆
        @{ from = @(25, 17, 15.4);   to = @(26, 25, 16.6);   tex = 'copper' }, # 右立杆
        @{ from = @(6, 24, 15.4);    to = @(26, 25.2, 16.6); tex = 'copper' }, # 顶横杆
        @{ from = @(6, 17, 15.4);    to = @(26, 18.2, 16.6); tex = 'copper' }  # 底横杆
    )

    # 发酵罐 · 顶置搅拌桨（Y，2.5°/t）
    'fermenter_agitator' = @(
        @{ from = @(15.3, 28, 15.3); to = @(16.7, 30, 16.7); tex = 'iron' },   # 轴
        @{ from = @(14.5, 29.5, 14.5);to = @(17.5, 30.4, 17.5); tex = 'copper' },# 轮毂
        @{ from = @(12, 29.7, 15.2); to = @(20, 30.4, 16.8); tex = 'copper' }, # 桨叶（横，半径 4）
        @{ from = @(15.2, 29.7, 12); to = @(16.8, 30.4, 20); tex = 'copper' }  # 桨叶（纵）
    )
    # 发酵罐 · 高位飞轮联轴节（Y，6°/t；半径 3；位于桨叶之上，互不重叠）
    'fermenter_coupling' = @(
        @{ from = @(13, 30.4, 13);   to = @(19, 32, 19);     tex = 'dark' }    # 飞轮盘
    )

    # 分解仪 · 卧式转鼓 + 锥段（X，14°/t；枢轴 = 转鼓中心 y18 / z16；两端内收避开轴承柱）
    # 转桶放大：断面 9 × 10（原 8 × 6），扫掠半径 6.7，仍小于机架/触点的最近距离 7.9
    'decomposer_drum' = @(
        @{ from = @(8.8, 13.5, 11);   to = @(23.4, 22.5, 21);  tex = 'dark' },   # 转鼓
        @{ from = @(23.4, 14.5, 12);  to = @(25.4, 21.5, 20);  tex = 'dark' },   # 锥段（粗）
        @{ from = @(25.4, 15.5, 13.5);to = @(26.8, 20.5, 18.5);tex = 'dark' },   # 锥段（细）
        @{ from = @(8.9, 15, 10.6);   to = @(23.3, 16.5, 21.4);tex = 'copper' }, # 加强箍 1
        @{ from = @(8.9, 19.5, 10.6); to = @(23.3, 21, 21.4); tex = 'copper' }   # 加强箍 2
    )
    # 分解仪 · 同轴驱动皮带轮（X，14°/t；位于转鼓西端内侧，与轴承柱/电机均不重叠）
    'decomposer_pulley' = @(
        @{ from = @(8.8, 15.8, 14);  to = @(9.8, 20.2, 18);  tex = 'dark' }    # 皮带轮
    )

    # 灭菌罐 · 舱门手轮（Z，2°/t；枢轴 z3.35；贴门心轴，不悬空）
    'sterilizer_handwheel' = @(
        @{ from = @(9, 15, 2.8);     to = @(23, 17, 3.9);    tex = 'dark' },   # 横杆
        @{ from = @(15, 9, 2.8);     to = @(17, 23, 3.9);    tex = 'dark' },   # 竖杆
        @{ from = @(14.4, 14.4, 2.4);to = @(17.6, 17.6, 3.9);tex = 'dark' }    # 轮毂
    )
    # 灭菌罐 · 压力表指针（Y 摆动，振幅 34°；位于罐体顶部表盘之上，枢轴 x13.7 / z14.0）
    'sterilizer_needle' = @(
        @{ from = @(13.5, 29.2, 13.9); to = @(13.9, 29.8, 15.5); tex = 'light' }  # 指针
    )

    # 结晶机 · 顶部晶体簇（Y，1°/t；整体内收；**起点抬到平台之上**，旋转时不切平台）
    'crystallizer_crystal' = @(
        @{ from = @(9, 26.2, 9);     to = @(13, 30.4, 13);   tex = 'cold' },   # 晶体 ×4
        @{ from = @(19, 26.2, 9);    to = @(23, 30.4, 13);   tex = 'cold' },
        @{ from = @(9, 26.2, 19);    to = @(13, 30.4, 23);   tex = 'cold' },
        @{ from = @(19, 26.2, 19);   to = @(23, 30.4, 23);   tex = 'cold' },
        @{ from = @(13.5, 26.2, 13.5);to = @(18.5, 31.4, 18.5);tex = 'cold' }, # 主晶体
        @{ from = @(14.8, 31.4, 14.8);to = @(17.2, 32, 17.2); tex = 'cold' }   # 晶体尖
    )
    # 结晶机 · 底部星型卸料阀（Y，4°/t；半径 3；位于锥段 3 下方，互不重叠）
    'crystallizer_valve' = @(
        @{ from = @(15.2, 0, 15.2);  to = @(16.8, 1.9, 16.8); tex = 'iron' },  # 阀轴
        @{ from = @(13, 0.5, 15.2);  to = @(19, 1.7, 16.8);   tex = 'copper' },# 星轮（横）
        @{ from = @(15.2, 0.5, 13);  to = @(16.8, 1.7, 19);   tex = 'copper' } # 星轮（纵）
    )

    # 生物反应器 · 贯通搅拌轴 + **四层桨叶**（Y，3°/t；桨叶半径 6.4，笼内可见）
    'bioreactor_agitator' = @(
        @{ from = @(15.4, 9, 15.4);  to = @(16.6, 30, 16.6);   tex = 'iron' },   # 贯通轴
        @{ from = @(9.6, 12.5, 15.2);to = @(22.4, 14, 16.8);   tex = 'copper' }, # 桨叶 1（横）
        @{ from = @(15.2, 12.5, 9.6);to = @(16.8, 14, 22.4);   tex = 'copper' }, # 桨叶 1（纵）
        @{ from = @(9.6, 16, 15.2);  to = @(22.4, 17.5, 16.8); tex = 'copper' }, # 桨叶 2（横）
        @{ from = @(15.2, 16, 9.6);  to = @(16.8, 17.5, 22.4); tex = 'copper' }, # 桨叶 2（纵）
        @{ from = @(9.6, 19.5, 15.2);to = @(22.4, 21, 16.8);   tex = 'copper' }, # 桨叶 3（横）
        @{ from = @(15.2, 19.5, 9.6);to = @(16.8, 21, 22.4);   tex = 'copper' }, # 桨叶 3（纵）
        @{ from = @(9.6, 23, 15.2);  to = @(22.4, 24.5, 16.8); tex = 'copper' }, # 桨叶 4（横）
        @{ from = @(15.2, 23, 9.6);  to = @(16.8, 24.5, 22.4); tex = 'copper' }  # 桨叶 4（纵）
    )
    # 生物反应器 · 底部气体分布器（Y，6°/t；半径 6，位于环箍 1 之下的敞口区）
    'bioreactor_sparger' = @(
        @{ from = @(14.5, 8.6, 14.5);to = @(17.5, 10.4, 17.5); tex = 'iron' },   # 中心毂
        @{ from = @(10, 9, 15.2);    to = @(14.5, 10, 16.8);   tex = 'copper' }, # 分布臂 ×4
        @{ from = @(17.5, 9, 15.2);  to = @(22, 10, 16.8);     tex = 'copper' },
        @{ from = @(15.2, 9, 10);    to = @(16.8, 10, 14.5);   tex = 'copper' },
        @{ from = @(15.2, 9, 17.5);  to = @(16.8, 10, 22);     tex = 'copper' }
    )
}

# 底部铜管连接件（照燃烧引擎的管口样式）：每面底部 2 个，从铜框架伸入机体 ——
# 既提供「铜管连接材质」的细节，又把**机体与铜框架连成一体**（否则机体会与框架脱开）。
# 值 = 从该面框架内沿伸入的深度（要够到机体，各机不同）。
$bottomReach = @{ slurry_mixer = 5; fermenter = 7; decomposer = 5; sterilizer = 10; crystallizer = 8; reactor = 7 }

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

function Write-Model($id, $boxes, $renderType = $null) {
    $raw = @()
    foreach ($b in $boxes) { $raw += New-Element $b.from $b.to $b.tex }

    # 逐元素「微偏移」：第 k 个元素整体**向内**平移 k*0.00137（<0.07px，肉眼不可见），
    # 保证任意两个元素不再有精确共面的面（共面 = z-fighting 闪烁）。
    # 注意：必须向内（负向）——原版模型元素坐标上限是 32，向外偏移会越界导致模型加载失败。
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

    # ambientocclusion = $false：**必须**。模型是 2×2×2（大于 1 格），原版会按「本方块那一格」的邻居
    # 给整个模型算环境光遮蔽（AO），于是朝向机体自己占地范围（+X/+Y/+Z）的面被压黑 —— 就是"太黑了"。
    # 这是社区老问题 "Multiblock Shadows Problem" 的标准解法（另一条路是像 Create 那样整机走 BER）。
    if ($renderType) {
        $model = [pscustomobject]@{ parent = 'minecraft:block/block'; ambientocclusion = $false; render_type = $renderType; textures = $texObj; elements = $elements }
    } else {
        $model = [pscustomobject]@{ parent = 'minecraft:block/block'; ambientocclusion = $false; textures = $texObj; elements = $elements }
    }
    [System.IO.File]::WriteAllText((Join-Path $modelsDir "$id.json"), ($model | ConvertTo-Json -Depth 12), $UTF8)
}

function Write-SingleState($id) {
    # 2×2×2 超格模型不能用「朝向 y 旋转」（原版绕方块中心转，会把整机甩出去）→ 单变体、空属性名。
    # 空属性名 [pscustomobject] 不允许 → 用哈希表。
    $state = @{ variants = @{ '' = @{ model = "stellar_evolution:block/$id" } } }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "$id.json"), ($state | ConvertTo-Json -Depth 8), $UTF8)
}

# ── 铜管接管口 ──────────────────────────────────────────────────────────────
# 机器占 2×2×2（主方块在最小角）。接点 = 四个水平侧面「最底一排」各 2 个（共 8）+ 底面 4 个，**按口独立**。
#   · 侧面接点在模型 y = 6..10、两个底格的格心（x/z = 6..10 或 22..26）。
#     **必须**放在 6..10 —— 铜管管臂的中心也在 6..10，否则管子接过来会上下错位一截。
#   · 每个侧面接点带一根「立式铜座」strap，从框架底梁（y 1..2）一直伸到管高 —— 否则会浮空。
#   · 底面 4 个接点没有铜座（裙座就是结构），头部向下凸出 1px，避开裙座底面 y=0 的共面闪烁。
# 每个口两态：open（**未接**：露出的 4×4 铜环 + 内凹深孔）/ capped（**已接**：被铜管封成一块铜垫）。
# 头部外侧面一律内缩 0.2px —— 贴着机器面会和铜管管臂的端面精确共面，插上管子就闪。
# 「一台一台做」：现在 6 台生化机器全部产接管口（用户指定：接管口美术要在所有大机器上复用）。
$portMachines = @('slurry_mixer', 'fermenter', 'decomposer', 'sterilizer', 'crystallizer', 'reactor')

# 12 个接点：名字 = 方块状态属性名，与 Java 端 BioMachineBlocks.PORTS 一一对应（两处必须一致）。
# head = 头部 4×4×2 的盒子；strap = 立式铜座（底面接点为空）；axis = 厚度轴；positive = 外侧是否在坐标大的一端。
$ports = [ordered]@{
    port_north_a = @{ axis = 'z'; positive = $false; head = @(6,    6, 0.2,  10,  10, 2.2);  strap = @(6,    1, 0.2,  10,  7, 1.8) }
    port_north_b = @{ axis = 'z'; positive = $false; head = @(22,   6, 0.2,  26,  10, 2.2);  strap = @(22,   1, 0.2,  26,  7, 1.8) }
    port_east_a  = @{ axis = 'x'; positive = $true;  head = @(29.8, 6, 6,    31.8, 10, 10);   strap = @(30.2, 1, 6,    31.8, 7, 10) }
    port_east_b  = @{ axis = 'x'; positive = $true;  head = @(29.8, 6, 22,   31.8, 10, 26);   strap = @(30.2, 1, 22,   31.8, 7, 26) }
    port_south_a = @{ axis = 'z'; positive = $true;  head = @(6,    6, 29.8, 10,  10, 31.8);  strap = @(6,    1, 30.2, 10,  7, 31.8) }
    port_south_b = @{ axis = 'z'; positive = $true;  head = @(22,   6, 29.8, 26,  10, 31.8);  strap = @(22,   1, 30.2, 26,  7, 31.8) }
    port_west_a  = @{ axis = 'x'; positive = $false; head = @(0.2,  6, 6,    2.2,  10, 10);    strap = @(0.2,  1, 6,    1.8,  7, 10) }
    port_west_b  = @{ axis = 'x'; positive = $false; head = @(0.2,  6, 22,   2.2,  10, 26);    strap = @(0.2,  1, 22,   1.8,  7, 26) }
    # 底面 4 个接点：x/z 各**内缩 0.3**（3.4 格宽）—— 否则接点的侧面会和裙座的侧面
    # （裙座占 x/z 6..26，即 x=6/26、z=6/26 四个面）精确共面。跨模型（机体的裙座 vs 接点模型）共面
    # 没法靠 Write-Model 的逐元素微偏移抵消，一放下去就闪。
    port_down_a  = @{ axis = 'y'; positive = $false; head = @(6.3,  -1, 6.3,  9.7,  0.6, 9.7);  strap = @() }
    port_down_b  = @{ axis = 'y'; positive = $false; head = @(22.3, -1, 6.3,  25.7, 0.6, 9.7);  strap = @() }
    port_down_c  = @{ axis = 'y'; positive = $false; head = @(6.3,  -1, 22.3, 9.7,  0.6, 25.7); strap = @() }
    port_down_d  = @{ axis = 'y'; positive = $false; head = @(22.3, -1, 22.3, 25.7, 0.6, 25.7); strap = @() }
}

# 单个口的元素。$bx = @(x0, y0, z0, x1, y1, z1)（该口 4×4×2 的包围盒）；
# $axis = 厚度轴；$outwardPositive = 外侧是否在坐标大的一端。
# 局部变量一律用 px/py/pz 前缀，避免和脚本里其它同名变量打架（曾经的踩坑）。
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
        $els += @{ from = @($px0, $py0, $pz0);           to = @($px1, ($py0 + 1), $pz1);       tex = 'copper' }
        $els += @{ from = @($px0, ($py1 - 1), $pz0);       to = @($px1, $py1, $pz1);           tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), $pz0);       to = @(($px0 + 1), ($py1 - 1), $pz1);   tex = 'copper' }
        $els += @{ from = @(($px1 - 1), ($py0 + 1), $pz0);   to = @($px1, ($py1 - 1), $pz1);       tex = 'copper' }
        if ($outwardPositive) { $els += @{ from = @(($px0 + 1), ($py0 + 1), $pz0);     to = @(($px1 - 1), ($py1 - 1), ($pz1 - 1)); tex = 'dark' } }
        else                  { $els += @{ from = @(($px0 + 1), ($py0 + 1), ($pz0 + 1)); to = @(($px1 - 1), ($py1 - 1), $pz1);     tex = 'dark' } }
    } elseif ($axis -eq 'x') {
        $els += @{ from = @($px0, $py0, $pz0);           to = @($px1, ($py0 + 1), $pz1);       tex = 'copper' }
        $els += @{ from = @($px0, ($py1 - 1), $pz0);       to = @($px1, $py1, $pz1);           tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), $pz0);       to = @($px1, ($py1 - 1), ($pz0 + 1));   tex = 'copper' }
        $els += @{ from = @($px0, ($py0 + 1), ($pz1 - 1));   to = @($px1, ($py1 - 1), $pz1);       tex = 'copper' }
        if ($outwardPositive) { $els += @{ from = @($px0, ($py0 + 1), ($pz0 + 1));         to = @(($px1 - 1), ($py1 - 1), ($pz1 - 1)); tex = 'dark' } }
        else                  { $els += @{ from = @(($px0 + 1), ($py0 + 1), ($pz0 + 1));     to = @($px1, ($py1 - 1), ($pz1 - 1));     tex = 'dark' } }
    } else {
        $els += @{ from = @($px0, $py0, $pz0);           to = @(($px0 + 1), $py1, $pz1);       tex = 'copper' }
        $els += @{ from = @(($px1 - 1), $py0, $pz0);       to = @($px1, $py1, $pz1);           tex = 'copper' }
        $els += @{ from = @(($px0 + 1), $py0, $pz0);       to = @(($px1 - 1), $py1, ($pz0 + 1));   tex = 'copper' }
        $els += @{ from = @(($px0 + 1), $py0, ($pz1 - 1));   to = @(($px1 - 1), $py1, $pz1);       tex = 'copper' }
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
    # 注意：**不要**给机体加 render_type=cutout —— 实测 cutout 层会让整机明显变暗（AO/平滑光照失效），
    # 玻璃视窗用「磨砂」效果（solid 层下 glass 贴图呈亮白色），与燃烧引擎亮度一致。
    $hasPorts = $portMachines -contains $id
    # 底部 8 个铜管连接件：既是低位接口，也是**机体与铜框架连成一体**的桥梁（少一个都会散架/浮空）
    $boxes = @($frame) + $bodies[$id] + (Get-BottomConnectors $bottomReach[$id])

    Write-Model $id $boxes
    if ($hasPorts) {
        Write-PortModels $id $boxes
        Write-PortState $id
        Write-Host ("  {0}  机体 {1} 块 + 接管口模型 25 个" -f $id, $boxes.Count)
    } else {
        Write-SingleState $id
        Write-Host ("  {0}  机体 {1} 块" -f $id, $boxes.Count)
    }

    $itemModelId = if ($hasPorts) { "${id}_item" } else { $id }
    $item = [pscustomobject]@{ model = [pscustomobject]@{ type = 'minecraft:model'; model = "stellar_evolution:block/$itemModelId" } }
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
# （实际渲染走 RenderShape.INVISIBLE，不画任何东西）
$shellModel = [pscustomobject]@{ parent = 'minecraft:block/block'; textures = [pscustomobject]@{ particle = $T['iron'] } }
[System.IO.File]::WriteAllText((Join-Path $modelsDir 'bio_machine_shell.json'), ($shellModel | ConvertTo-Json -Depth 6), $UTF8)
foreach ($id in $bodies.Keys) {
    $shellState = @{ variants = @{ '' = @{ model = 'stellar_evolution:block/bio_machine_shell' } } }
    [System.IO.File]::WriteAllText((Join-Path $statesDir "${id}_shell.json"), ($shellState | ConvertTo-Json -Depth 6), $UTF8)
    Write-Host ("  {0}_shell  空模型 + 单变体" -f $id)
}

Write-Host ("完成：{0} 台机器 + {1} 个可动部件" -f $bodies.Count, $parts.Count)

﻿$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dataDir = Join-Path $root 'src/main/resources/data/stellar_evolution/advancement'
$langZh = Join-Path $root 'src/main/resources/assets/stellar_evolution/lang/zh_cn.json'
$langEn = Join-Path $root 'src/main/resources/assets/stellar_evolution/lang/en_us.json'

# 树状进度（2026-10-24 重构）：按「实际配方依赖 / 可平行」分支，不再是一条线。
# 主要分支：能源（热机 → 管道 / 储罐）、结构（铜框架 → 热室 → 钢 → 轴承 / 框架 / 活塞）、
# 电气（电容 / 传感器 / 钨线电阻 / 伺服 / 温控 / 控制模块）、钨（矿 → 锭 → 精密框架 / 电阻）、
# 生化、石化、煤化工、裂变。裂变燃料的工艺段顺序保留，支撑件（锆管 / 控制棒 / 石墨棒 / 流体单元）单独成枝。
# id / parent / icon / frame / trigger kind / item / zh title / zh desc / en title / en desc
$advancements = @(
    @{ id='root'; parent=''; icon='minecraft:copper_ingot'; frame='task'; kind='tick'; item='';
       zhT='恒星演变'; zhD='从一块铜锭开始'; enT='Stellar Evolution'; enD='It starts with a copper ingot' },

    # ---------------- 能源分支
    @{ id='energy/heat_engine'; parent='root'; icon='stellar_evolution:heat_engine'; frame='task'; kind='item'; item='stellar_evolution:heat_engine';
       zhT='第一把火'; zhD='让热压差转起来'; enT='First Fire'; enD='Get heat pressure moving' },
    @{ id='energy/copper_pipe'; parent='energy/heat_engine'; icon='stellar_evolution:copper_pipe'; frame='task'; kind='item'; item='stellar_evolution:copper_pipe';
       zhT='四通八达'; zhD='把热压差接进机器'; enT='Plumbing'; enD='Run heat pressure into a machine' },
    @{ id='energy/tank'; parent='energy/copper_pipe'; icon='stellar_evolution:tank'; frame='task'; kind='item'; item='stellar_evolution:tank';
       zhT='有容乃大'; zhD='存下第一桶流体'; enT='Room to Spare'; enD='Store your first bucket of fluid' },
    @{ id='energy/burning_engine'; parent='industry/piston'; icon='stellar_evolution:burning_engine'; frame='task'; kind='item'; item='stellar_evolution:burning_engine';
       zhT='火上浇油'; zhD='用活塞组件和电容升级热机'; enT='Add Fuel to the Fire'; enD='Upgrade the heat engine with pistons and capacitors' },

    # ---------------- 结构 / 热室 / 钢分支
    @{ id='industry/copper_frame'; parent='root'; icon='stellar_evolution:copper_frame'; frame='task'; kind='item'; item='stellar_evolution:copper_frame';
       zhT='铜骨'; zhD='用铜块和铜板搭个框架'; enT='Copper Bones'; enD='Build a frame from copper blocks and plates' },
    @{ id='energy/hot_chamber'; parent='industry/copper_frame'; icon='minecraft:copper_chest'; frame='task'; kind='item'; item='minecraft:copper_chest';
       zhT='热箱'; zhD='把铜箱子放在铜块上'; enT='Hot Box'; enD='Set a copper chest on copper blocks' },
    @{ id='industry/steel'; parent='energy/hot_chamber'; icon='stellar_evolution:steel_ingot'; frame='goal'; kind='item'; item='stellar_evolution:steel_ingot';
       zhT='百炼成钢'; zhD='用铁和煤炼出钢'; enT='Forged in Heat'; enD='Make steel from iron and coal' },

    # 钢 → 三条平行结构线：轴承 / 机械框架 / 活塞
    @{ id='industry/bearing'; parent='industry/steel'; icon='stellar_evolution:bearing'; frame='task'; kind='item'; item='stellar_evolution:bearing';
       zhT='转起来'; zhD='用钢齿轮和钢杆做轴承'; enT='Let It Spin'; enD='Make a bearing from a steel gear and rods' },
    @{ id='industry/machine_frame'; parent='industry/steel'; icon='stellar_evolution:machine_frame'; frame='task'; kind='item'; item='stellar_evolution:machine_frame';
       zhT='骨架'; zhD='把钢板和钢杆焊成框架'; enT='Bare Bones'; enD='Weld plates and rods into a frame' },
    @{ id='industry/piston'; parent='industry/steel'; icon='stellar_evolution:piston_assembly'; frame='task'; kind='item'; item='stellar_evolution:piston_assembly';
       zhT='推一把'; zhD='用钢板、钢杆、铜齿轮和铁板做活塞'; enT='Push Comes to Shove'; enD='Make a piston from plates, rods, a copper gear and an iron plate' },

    # 活塞 → 三台平行的材料加工机（没有强制先后）
    @{ id='industry/crusher'; parent='industry/piston'; icon='stellar_evolution:crusher'; frame='task'; kind='item'; item='stellar_evolution:crusher';
       zhT='碎石'; zhD='把矿石嚼碎'; enT='Rock Bottom'; enD='Chew up some ore' },
    @{ id='industry/die_press'; parent='industry/piston'; icon='stellar_evolution:die_press'; frame='task'; kind='item'; item='stellar_evolution:die_press';
       zhT='压成片'; zhD='把锭压成板'; enT='Flat Out'; enD='Press an ingot flat' },
    @{ id='industry/rolling_mill'; parent='industry/piston'; icon='stellar_evolution:rolling_mill'; frame='task'; kind='item'; item='stellar_evolution:rolling_mill';
       zhT='拉成丝'; zhD='把锭拉成线'; enT='Wire It Up'; enD='Roll an ingot into wire' },

    # ---------------- 电气 / 控制分支（电容 / 传感器 / 钨电阻并行）
    @{ id='industry/capacitor'; parent='energy/hot_chamber'; icon='stellar_evolution:capacitor'; frame='task'; kind='item'; item='stellar_evolution:capacitor';
       zhT='存点电'; zhD='用铜板、铁板和玻璃做电容'; enT='Hold a Charge'; enD='Make a capacitor from copper plates, iron plates and glass' },
    @{ id='industry/sensor'; parent='energy/hot_chamber'; icon='stellar_evolution:sensor'; frame='task'; kind='item'; item='stellar_evolution:sensor';
       zhT='感知'; zhD='用铜、石英和金板做传感器'; enT='Sense'; enD='Make a sensor from copper, quartz and a gold plate' },
    @{ id='industry/servo'; parent='industry/bearing'; icon='stellar_evolution:servo'; frame='task'; kind='item'; item='stellar_evolution:servo';
       zhT='听话的关节'; zhD='用轴承、钨齿轮/杆/线和金线做伺服'; enT='At Your Service'; enD='Make a servo from a bearing, tungsten gear/rod/wire and gold wire' },
    @{ id='industry/thermostat'; parent='industry/sensor'; icon='stellar_evolution:thermostat'; frame='task'; kind='item'; item='stellar_evolution:thermostat';
       zhT='知冷知热'; zhD='用传感器、钨和下界合金做温控器'; enT='Knows Hot from Cold'; enD='Make a thermostat from a sensor, tungsten and netherite' },
    @{ id='industry/control_module'; parent='industry/sensor'; icon='stellar_evolution:control_module'; frame='task'; kind='item'; item='stellar_evolution:control_module';
       zhT='控制核心'; zhD='把传感器、电阻、电容和钨/下界合金接起来'; enT='The Brain'; enD='Wire a sensor, resistor, capacitor, tungsten and netherite together' },

    # ---------------- 钨分支（矿 → 锭 → 精密框架 / 电阻）
    @{ id='industry/tungsten_ore'; parent='root'; icon='stellar_evolution:tungsten_ore'; frame='task'; kind='item'; item='stellar_evolution:tungsten_ore';
       zhT='钨矿'; zhD='挖一块钨矿'; enT='Tungsten Ore'; enD='Mine a chunk of tungsten ore' },
    @{ id='industry/tungsten_ingot'; parent='industry/tungsten_ore'; icon='stellar_evolution:tungsten_ingot'; frame='goal'; kind='item'; item='stellar_evolution:tungsten_ingot';
       zhT='钨锭'; zhD='把钨精炼成锭'; enT='Tungsten Ingot'; enD='Refine tungsten into an ingot' },
    @{ id='industry/precision_heavy_frame'; parent='industry/tungsten_ingot'; icon='stellar_evolution:precision_heavy_frame'; frame='goal'; kind='item'; item='stellar_evolution:precision_heavy_frame';
       zhT='精密重型框架'; zhD='用钨和下界合金造重型框架'; enT='Precision Heavy Frame'; enD='Build a heavy frame from tungsten and netherite' },
    @{ id='industry/resistor'; parent='industry/tungsten_ingot'; icon='stellar_evolution:resistor'; frame='task'; kind='item'; item='stellar_evolution:resistor';
       zhT='阻尼'; zhD='用铜线、钨线和煤做电阻'; enT='Resistance'; enD='Make a resistor from copper wire, tungsten wire and coal' },

    # ---------------- 生物质 / 生化分支
    @{ id='industry/biomass'; parent='industry/crusher'; icon='stellar_evolution:biomass'; frame='task'; kind='item'; item='stellar_evolution:biomass';
       zhT='绿色原料'; zhD='收一捧生物质'; enT='Green Thumb'; enD='Gather some biomass' },
    @{ id='industry/mechanical_farmland'; parent='industry/biomass'; icon='stellar_evolution:mechanical_farmland'; frame='task'; kind='item'; item='stellar_evolution:mechanical_farmland';
       zhT='机械种田'; zhD='让土壤自己种地'; enT='Farming, Automated'; enD='Let the soil farm itself' },
    @{ id='bio/slurry_mixer'; parent='industry/servo'; icon='stellar_evolution:slurry_mixer'; frame='task'; kind='item'; item='stellar_evolution:slurry_mixer';
       zhT='搅一搅'; zhD='把生物质打成浆'; enT='Stir It Up'; enD='Turn biomass into slurry' },
    @{ id='bio/fermenter'; parent='industry/thermostat'; icon='stellar_evolution:fermenter'; frame='task'; kind='item'; item='stellar_evolution:fermenter';
       zhT='让它发酵'; zhD='等它慢慢发酵'; enT='Let It Sit'; enD='Let it ferment' },
    @{ id='bio/sterilizer'; parent='industry/thermostat'; icon='stellar_evolution:sterilizer'; frame='task'; kind='item'; item='stellar_evolution:sterilizer';
       zhT='高温消毒'; zhD='先灭个菌'; enT='Clean Freak'; enD='Kill the germs first' },
    @{ id='bio/decomposer'; parent='industry/servo'; icon='stellar_evolution:decomposer'; frame='task'; kind='item'; item='stellar_evolution:decomposer';
       zhT='拆开看看'; zhD='把浆拆成肥料'; enT='Break It Down'; enD='Break slurry into fertilizer' },
    @{ id='bio/crystallizer'; parent='industry/bearing'; icon='stellar_evolution:crystallizer'; frame='task'; kind='item'; item='stellar_evolution:crystallizer';
       zhT='析出晶体'; zhD='把固体析出来'; enT='Crystal Clear'; enD='Crystallize the solids out' },
    @{ id='bio/reactor'; parent='industry/control_module'; icon='stellar_evolution:reactor'; frame='goal'; kind='item'; item='stellar_evolution:reactor';
       zhT='它活了'; zhD='让菌群开工'; enT='It Is Alive'; enD='Put the culture to work' },
    @{ id='bio/bioplastic'; parent='bio/reactor'; icon='stellar_evolution:bioplastic_pellets'; frame='task'; kind='item'; item='stellar_evolution:bioplastic_pellets';
       zhT='种出来的塑料'; zhD='收一把生物塑料'; enT='Grown Plastic'; enD='Collect bioplastic pellets' },

    # ---------------- 石化分支
    @{ id='petro/distillation'; parent='industry/thermostat'; icon='stellar_evolution:distillation_tower'; frame='goal'; kind='item'; item='stellar_evolution:distillation_tower';
       zhT='分家'; zhD='把原油分层'; enT='Crude Awakening'; enD='Split crude into layers' },
    @{ id='petro/cracking'; parent='industry/control_module'; icon='stellar_evolution:cracking_furnace'; frame='task'; kind='item'; item='stellar_evolution:cracking_furnace';
       zhT='裂开'; zhD='把重油打断'; enT='Cracked'; enD='Break the heavy fractions' },
    @{ id='petro/reformer'; parent='industry/control_module'; icon='stellar_evolution:reformer'; frame='task'; kind='item'; item='stellar_evolution:reformer';
       zhT='重整旗鼓'; zhD='把石脑油重整'; enT='Rearranged'; enD='Reform the naphtha' },
    @{ id='petro/polymerizer'; parent='industry/control_module'; icon='stellar_evolution:polymerizer'; frame='task'; kind='item'; item='stellar_evolution:polymerizer';
       zhT='串起来'; zhD='把小分子串成链'; enT='Chain Gang'; enD='String the monomers together' },
    @{ id='petro/plastics'; parent='petro/polymerizer'; icon='stellar_evolution:bakelite'; frame='goal'; kind='item'; item='stellar_evolution:bakelite';
       zhT='可塑之材'; zhD='收下第一种聚合物'; enT='Plastic Fantastic'; enD='Collect your first polymer' },

    # ---------------- 跨线组件（生物塑料 / 石化塑料 / 钨）
    @{ id='industry/electric_motor'; parent='bio/bioplastic'; icon='stellar_evolution:electric_motor'; frame='task'; kind='item'; item='stellar_evolution:electric_motor';
       zhT='通电'; zhD='用生物塑料和钨线让电转起来'; enT='Power Up'; enD='Use bioplastic and tungsten wire to turn electricity into motion' },
    @{ id='industry/flux_coil'; parent='petro/plastics'; icon='stellar_evolution:flux_coil'; frame='task'; kind='item'; item='stellar_evolution:flux_coil';
       zhT='绕线圈'; zhD='用电木、铜线和金线绕线圈'; enT='Coil Up'; enD='Wind a coil from bakelite, copper wire and gold wire' },
    @{ id='industry/pcb'; parent='petro/plastics'; icon='stellar_evolution:pcb'; frame='task'; kind='item'; item='stellar_evolution:pcb';
       zhT='布线'; zhD='在基材上铺线路'; enT='Wiring'; enD='Lay traces on a substrate' },
    @{ id='industry/chip'; parent='industry/pcb'; icon='stellar_evolution:chip'; frame='task'; kind='item'; item='stellar_evolution:chip';
       zhT='塞进芯片'; zhD='用金线、钨线和下界合金板塞进芯片'; enT='Tiny Brains'; enD='Pack logic with gold wire, tungsten wire and a netherite plate' },
    @{ id='industry/fluid_cell'; parent='industry/thermostat'; icon='stellar_evolution:fluid_cell'; frame='task'; kind='item'; item='stellar_evolution:fluid_cell';
       zhT='装一罐'; zhD='用玻璃、钢板、丁腈橡胶和钨板做流体单元'; enT='Cell Mate'; enD='Make a fluid cell from glass, steel, NBR and a tungsten plate' },

    # ---------------- 煤化工分支
    @{ id='coal/gasifier'; parent='industry/flux_coil'; icon='stellar_evolution:gasifier'; frame='task'; kind='item'; item='stellar_evolution:gasifier';
       zhT='煤气十足'; zhD='把煤变成气'; enT='Gas Up'; enD='Turn coal into gas' },
    @{ id='coal/synthesis_tower'; parent='industry/control_module'; icon='stellar_evolution:synthesis_tower'; frame='task'; kind='item'; item='stellar_evolution:synthesis_tower';
       zhT='搭塔造物'; zhD='把合成气搭成料'; enT='Build It Up'; enD='Build materials from syngas' },
    @{ id='coal/urea'; parent='coal/synthesis_tower'; icon='stellar_evolution:urea'; frame='task'; kind='item'; item='stellar_evolution:urea';
       zhT='肥料'; zhD='收一袋尿素'; enT='Fertilizer'; enD='Collect a bag of urea' },

    # ---------------- 裂变分支：铀矿单独成枝；机器链从酸浸槽开始
    @{ id='fission/uranium_ore'; parent='root'; icon='stellar_evolution:uranium_ore'; frame='task'; kind='item'; item='stellar_evolution:uranium_ore';
       zhT='有点烫手'; zhD='挖一块铀矿'; enT='A Little Warm'; enD='Mine a chunk of uranium ore' },
    @{ id='fission/ore_leacher'; parent='fission/uranium_ore'; icon='stellar_evolution:ore_leacher'; frame='task'; kind='item'; item='stellar_evolution:ore_leacher';
       zhT='酸洗'; zhD='把铀泡出来'; enT='Acid Wash'; enD='Leach the uranium out' },
    @{ id='fission/yellowcake'; parent='fission/ore_leacher'; icon='stellar_evolution:yellowcake'; frame='task'; kind='item'; item='stellar_evolution:yellowcake';
       zhT='黄饼'; zhD='收一撮黄饼'; enT='Yellowcake'; enD='Collect some yellowcake' },
    @{ id='fission/centrifuge'; parent='fission/ore_leacher'; icon='stellar_evolution:fission_centrifuge'; frame='task'; kind='item'; item='stellar_evolution:fission_centrifuge';
       zhT='转分家'; zhD='把同位素分开'; enT='Spin Cycle'; enD='Separate the isotopes' },
    @{ id='fission/u235'; parent='fission/centrifuge'; icon='stellar_evolution:u235'; frame='goal'; kind='item'; item='stellar_evolution:u235';
       zhT='会裂的铀'; zhD='收一撮铀-235'; enT='Fissile'; enD='Collect some uranium-235' },
    @{ id='fission/zircaloy_tube'; parent='industry/steel'; icon='stellar_evolution:zircaloy_tube'; frame='task'; kind='item'; item='stellar_evolution:zircaloy_tube';
       zhT='锆管'; zhD='用钢、石英和玻璃拉一根锆管'; enT='Zircaloy Tube'; enD='Draw a tube from steel, quartz and glass' },
    @{ id='fission/canning'; parent='fission/u235'; icon='stellar_evolution:canning_machine'; frame='task'; kind='item'; item='stellar_evolution:canning_machine';
       zhT='装罐'; zhD='把铀直接装进锆管'; enT='Tin Can'; enD='Load uranium into the tube' },
    @{ id='fission/fuel_rod'; parent='fission/canning'; icon='stellar_evolution:fuel_rod_heu'; frame='goal'; kind='item'; item='stellar_evolution:fuel_rod_heu';
       zhT='入堆许可'; zhD='做一根燃料棒'; enT='Reactor Ready'; enD='Assemble a fuel rod' },
    @{ id='hep/cyclotron'; parent='fission/canning'; icon='stellar_evolution:cyclotron'; frame='goal'; kind='item'; item='stellar_evolution:cyclotron';
       zhT='第一台加速器'; zhD='造一台回旋加速器'; enT='First Accelerator'; enD='Build a cyclotron' },
    @{ id='hep/heavy_water'; parent='fission/centrifuge'; icon='stellar_evolution:heavy_water_bucket'; frame='task'; kind='item'; item='stellar_evolution:heavy_water_bucket';
       zhT='重水'; zhD='从水里富集出重水'; enT='Heavy Water'; enD='Enrich heavy water from water' },
    @{ id='hep/deuterium'; parent='hep/heavy_water'; icon='stellar_evolution:deuterium_bucket'; frame='task'; kind='item'; item='stellar_evolution:deuterium_bucket';
       zhT='氘'; zhD='把重水分离成氘'; enT='Deuterium'; enD='Split heavy water into deuterium' },
    @{ id='hep/tritium'; parent='hep/cyclotron'; icon='stellar_evolution:tritium_bucket'; frame='task'; kind='item'; item='stellar_evolution:tritium_bucket';
       zhT='氚'; zhD='用回旋加速器轰出氚'; enT='Tritium'; enD='Make tritium with the cyclotron' },
    @{ id='hep/fusion_core'; parent='hep/tritium'; icon='stellar_evolution:dt_fusion_core'; frame='challenge'; kind='item'; item='stellar_evolution:dt_fusion_core';
       zhT='一颗小太阳'; zhD='做出氘氚聚变芯核'; enT='A Tiny Sun'; enD='Build a deuterium-tritium fusion core' },
    @{ id='fission/boron_carbide_rod'; parent='industry/steel'; icon='stellar_evolution:boron_carbide_rod'; frame='task'; kind='item'; item='stellar_evolution:boron_carbide_rod';
       zhT='控制棒'; zhD='用钢和石油焦烧一根控制棒'; enT='Control Rod'; enD='Sinter a control rod from steel and coke' },
    @{ id='fission/graphite_rod'; parent='petro/cracking'; icon='stellar_evolution:graphite_rod'; frame='task'; kind='item'; item='stellar_evolution:graphite_rod';
       zhT='石墨棒'; zhD='把石油焦压成石墨棒'; enT='Graphite Rod'; enD='Press coke into a graphite rod' },
    @{ id='fission/reactor'; parent='fission/fuel_rod'; icon='stellar_evolution:fission_reactor'; frame='goal'; kind='item'; item='stellar_evolution:fission_reactor';
       zhT='链式反应'; zhD='让堆芯热起来'; enT='Going Critical'; enD='Bring the core up' },
    @{ id='fission/reaction_chamber'; parent='fission/reactor'; icon='stellar_evolution:reaction_chamber'; frame='task'; kind='item'; item='stellar_evolution:reaction_chamber';
       zhT='燃料仓'; zhD='给反应堆接上燃料仓'; enT='Fuel Bay'; enD='Hook the fuel bay to the reactor' },
    @{ id='fission/steam_turbine'; parent='fission/reaction_chamber'; icon='stellar_evolution:steam_turbine_unit'; frame='goal'; kind='item'; item='stellar_evolution:steam_turbine_unit';
       zhT='蒸汽上岗'; zhD='让蒸汽推叶轮'; enT='Full Steam Ahead'; enD='Put steam to the blades' },
    @{ id='fission/800mw'; parent='fission/steam_turbine'; icon='minecraft:beacon'; frame='challenge'; kind='impossible'; item='';
       zhT='八百万瓦'; zhD='把单堆推到 800 MW，这只是路上的一站'; enT='Eight Hundred Megawatts'; enD='Push one reactor to 800 MW. This is just a stop on the way' }
)

# ------------------------------------------------------------------ 树结构校验
$nodeById = @{}
foreach ($a in $advancements) {
    if ($nodeById.ContainsKey($a.id)) { throw "duplicate advancement id: $($a.id)" }
    $nodeById[$a.id] = $a
}
foreach ($a in $advancements) {
    if ($a.parent -eq '') {
        if ($a.id -ne 'root') { throw "advancement without parent: $($a.id)" }
        continue
    }
    if (-not $nodeById.ContainsKey($a.parent)) { throw "advancement $($a.id) has missing parent: $($a.parent)" }
    $seen = @{}
    $cur = $a.id
    while ($cur -ne '') {
        if ($seen.ContainsKey($cur)) { throw "advancement cycle at: $cur" }
        $seen[$cur] = $true
        $cur = $nodeById[$cur].parent
    }
    if ($cur -ne '') { throw "advancement $($a.id) does not reach root" }
}
foreach ($a in $advancements) {
    if ($a.parent -ne '' -and $a.id -ne 'root') {
        $cur = $a.id
        while ($cur -ne 'root') {
            $cur = $nodeById[$cur].parent
            if ($cur -eq '') { throw "advancement $($a.id) is not connected to root" }
        }
    }
}

function New-AdvancementJson($a) {
    $key = 'advancement.stellar_evolution.' + ($a.id -replace '/', '.')
    $display = [ordered]@{
        icon = [ordered]@{ id = $a.icon }
        title = [ordered]@{ translate = "$key.title" }
        description = [ordered]@{ translate = "$key.description" }
        frame = $a.frame
        show_toast = $true
        announce_to_chat = $true
    }
    if ($a.id -eq 'root') {
        $display.background = 'minecraft:block/copper_block'
    }
    $obj = [ordered]@{ display = $display }
    if ($a.parent -ne '') {
        $obj.parent = "stellar_evolution:$($a.parent)"
    }
    switch ($a.kind) {
        'tick' { $obj.criteria = [ordered]@{ tick = [ordered]@{ trigger = 'minecraft:tick' } } }
        'impossible' { $obj.criteria = [ordered]@{ impossible = [ordered]@{ trigger = 'minecraft:impossible' } } }
        default {
            $obj.criteria = [ordered]@{
                unlock = [ordered]@{
                    trigger = 'minecraft:inventory_changed'
                    conditions = [ordered]@{ items = @([ordered]@{ items = $a.item }) }
                }
            }
        }
    }
    return ($obj | ConvertTo-Json -Depth 12)
}

if (!(Test-Path $dataDir)) {
    New-Item -ItemType Directory -Path $dataDir -Force | Out-Null
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$generated = @{}
foreach ($a in $advancements) {
    $path = Join-Path $dataDir ($a.id + '.json')
    $dir = Split-Path $path -Parent
    if (!(Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($path, (New-AdvancementJson $a), $utf8)
    $generated[$a.id] = $true
}

# 删掉上一版留下、这一版已经不用的进度文件（例如被合并 / 移除的节点）
Get-ChildItem -Recurse -File -Filter *.json $dataDir | ForEach-Object {
    $rel = $_.FullName.Substring($dataDir.Length + 1) -replace '\\', '/' -replace '\.json$', ''
    if (-not $generated.ContainsKey($rel)) {
        Remove-Item $_.FullName -Force
    }
}

function Update-Lang([string]$path, [bool]$isZh) {
    $text = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $text = [regex]::Replace($text, '^\s*"advancement\.stellar_evolution\.[^"]+":.*\r?\n', '', [System.Text.RegularExpressions.RegexOptions]::Multiline)
    $lines = New-Object System.Collections.Generic.List[string]
    foreach ($a in $advancements) {
        $key = 'advancement.stellar_evolution.' + ($a.id -replace '/', '.')
        $title = if ($isZh) { $a.zhT } else { $a.enT }
        $desc = if ($isZh) { $a.zhD } else { $a.enD }
        $lines.Add('  "' + $key + '.title": ' + (ConvertTo-Json $title -Compress))
        $lines.Add('  "' + $key + '.description": ' + (ConvertTo-Json $desc -Compress))
    }
    $idx = $text.LastIndexOf('}')
    $head = $text.Substring(0, $idx).TrimEnd()
    if ($head.EndsWith(',')) {
        $head = $head.Substring(0, $head.Length - 1).TrimEnd()
    }
    $separator = if ($head.EndsWith('{')) { "`r`n" } else { ",`r`n" }
    $new = $head + $separator + ($lines -join ",`r`n") + "`r`n}`r`n"
    [System.IO.File]::WriteAllText($path, $new, $utf8)
}

Update-Lang $langZh $true
Update-Lang $langEn $false

Write-Host ("advancements=" + $advancements.Count)

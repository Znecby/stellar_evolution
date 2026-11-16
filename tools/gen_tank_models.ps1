# gen_tank_models.ps1 —— 三档储罐（铜 / 钢 / 硬质）的方块模型 + 方块状态 + 物品模型。
#
# 三档只有金属贴图不同，几何完全一致（见 docs 开发文档 §6.4）：
#   * 每格罐 = 2 个金属盖（top / bottom 属性控制，只在整罐最上 / 最下层）+ 4 根角柱 + 4 面墙。
#   * 合并后「罐内」的面整面隐去（north / east / south / west 属性 = 该面有同属一个大罐的邻居）。
#   * 角柱在「相邻两面至少有一面朝外」时出现；墙分玻璃（window=true）与金属板（window=false）两种。
#   * 墙的玻璃面往里缩 0.02px：不缩会与盖子的外表面共面（贴图不同 -> 闪面），缩了就被盖子挡住。
#   * 玻璃照 26.x 口径写 force_translucent；玻璃面必须显式写 uv [0,0,16,16]。
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $root 'src\main\resources\assets\stellar_evolution'
$modelDir = Join-Path $assetRoot 'models\block'
$stateDir = Join-Path $assetRoot 'blockstates'
$itemDir = Join-Path $assetRoot 'items'
New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

$GLASS = 'minecraft:block/glass'

# 三档储罐：注册 id + 金属贴图
$tiers = @(
    @{ id = 'copper_tank';   metal = 'minecraft:block/copper_block' },
    @{ id = 'steel_tank';    metal = 'stellar_evolution:block/steel_block' },
    @{ id = 'hardened_tank'; metal = 'stellar_evolution:block/tungsten_copper_hard_alloy_block' }
)

# ---------------------------------------------------------------- 小工具
function Write-Json($path, $object) {
    $json = $object | ConvertTo-Json -Depth 40
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function MetalFace($cull) {
    $face = [ordered]@{ texture = '#metal' }
    if ($cull) { $face.cullface = $cull }
    return $face
}

function GlassFace($cull) {
    $face = [ordered]@{ texture = '#glass'; uv = @(0, 0, 16, 16) }
    if ($cull) { $face.cullface = $cull }
    return $face
}

function Element($from, $to, $faces) {
    return [ordered]@{ from = $from; to = $to; faces = $faces }
}

function Write-Model($dir, $name, $textures, $elements) {
    $model = [ordered]@{
        parent   = 'minecraft:block/block'
        textures = $textures
        elements = $elements
    }
    Write-Json (Join-Path $dir "$name.json") $model
}

# 四根角柱 / 四面墙的几何（三档共用）
$posts = @(
    @{ name = 'post_nw'; from = @(0, 0, 0);    to = @(4, 16, 4);
       outer = [ordered]@{ north = 'north'; west = 'west' };  inner = @('east', 'south') },
    @{ name = 'post_ne'; from = @(12, 0, 0);   to = @(16, 16, 4);
       outer = [ordered]@{ north = 'north'; east = 'east' };  inner = @('west', 'south') },
    @{ name = 'post_se'; from = @(12, 0, 12);  to = @(16, 16, 16);
       outer = [ordered]@{ south = 'south'; east = 'east' };  inner = @('west', 'north') },
    @{ name = 'post_sw'; from = @(0, 0, 12);   to = @(4, 16, 16);
       outer = [ordered]@{ south = 'south'; west = 'west' };  inner = @('east', 'north') }
)
$INSET = 0.02
$FAR = 16 - $INSET
$NEAR_LO = $INSET
$walls = @(
    @{ dir = 'north'; from = @(4, 0, $NEAR_LO); to = @(12, 16, 4);    outer = 'north'; inner = 'south' },
    @{ dir = 'south'; from = @(4, 0, 12);       to = @(12, 16, $FAR); outer = 'south'; inner = 'north' },
    @{ dir = 'west';  from = @($NEAR_LO, 0, 4); to = @(4, 16, 12);    outer = 'west';  inner = 'east' },
    @{ dir = 'east';  from = @(12, 0, 4);       to = @($FAR, 16, 12); outer = 'east';  inner = 'west' }
)

foreach ($tier in $tiers) {
    $id = $tier.id
    $metal = $tier.metal
    $NS = "stellar_evolution:block/$id"
    $dir = Join-Path $modelDir $id
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Write-Output "tier $id ($metal)"

    $texMetal = [ordered]@{ metal = $metal; particle = $metal }
    $texGlass = [ordered]@{
        metal    = $metal
        glass    = [ordered]@{ 'force_translucent' = $true; sprite = $GLASS }
        particle = $metal
    }

    # ------------------------------------------------------------ 盖（顶 / 底整片金属板）
    Write-Model $dir 'cap_bottom' $texMetal @(
        (Element @(0, 0, 0) @(16, 4, 16) ([ordered]@{
            down  = (MetalFace 'down')
            up    = (MetalFace $null)
            north = (MetalFace 'north')
            south = (MetalFace 'south')
            west  = (MetalFace 'west')
            east  = (MetalFace 'east')
        }))
    )
    Write-Model $dir 'cap_top' $texMetal @(
        (Element @(0, 12, 0) @(16, 16, 16) ([ordered]@{
            down  = (MetalFace $null)
            up    = (MetalFace 'up')
            north = (MetalFace 'north')
            south = (MetalFace 'south')
            west  = (MetalFace 'west')
            east  = (MetalFace 'east')
        }))
    )

    # ------------------------------------------------------------ 四根角柱
    foreach ($post in $posts) {
        $faces = [ordered]@{}
        foreach ($faceDir in @('north', 'east', 'south', 'west')) {
            if ($post.outer.Contains($faceDir)) {
                $faces[$faceDir] = (MetalFace $post.outer[$faceDir])
            } elseif ($post.inner -contains $faceDir) {
                $faces[$faceDir] = (MetalFace $null)
            }
        }
        Write-Model $dir $post.name $texMetal @((Element $post.from $post.to $faces))
    }

    # ------------------------------------------------------------ 四面墙（玻璃 / 金属板）
    foreach ($wall in $walls) {
        $glassFaces = [ordered]@{}
        $glassFaces[$wall.outer] = (GlassFace $wall.outer)
        $glassFaces[$wall.inner] = (GlassFace $null)
        Write-Model $dir "wall_$($wall.dir)_glass" $texGlass @((Element $wall.from $wall.to $glassFaces))

        $solidFaces = [ordered]@{}
        $solidFaces[$wall.outer] = (MetalFace $wall.outer)
        $solidFaces[$wall.inner] = (MetalFace $null)
        Write-Model $dir "wall_$($wall.dir)_solid" $texMetal @((Element $wall.from $wall.to $solidFaces))
    }

    # ------------------------------------------------------------ 完整方块模型（物品栏 / 投影用）
    $fullElements = New-Object System.Collections.ArrayList
    [void]$fullElements.Add((Element @(0, 0, 0) @(16, 4, 16) ([ordered]@{
        down  = (MetalFace 'down')
        up    = (MetalFace $null)
        north = (MetalFace 'north')
        south = (MetalFace 'south')
        west  = (MetalFace 'west')
        east  = (MetalFace 'east')
    })))
    [void]$fullElements.Add((Element @(0, 12, 0) @(16, 16, 16) ([ordered]@{
        down  = (MetalFace $null)
        up    = (MetalFace 'up')
        north = (MetalFace 'north')
        south = (MetalFace 'south')
        west  = (MetalFace 'west')
        east  = (MetalFace 'east')
    })))
    # 角柱在完整模型里只占盖与盖之间（y 4..12）
    $fullPosts = @(
        @{ from = @(0, 4, 0);   to = @(4, 12, 4);   outer = [ordered]@{ north = 'north'; west = 'west' } },
        @{ from = @(12, 4, 0);  to = @(16, 12, 4);  outer = [ordered]@{ north = 'north'; east = 'east' } },
        @{ from = @(12, 4, 12); to = @(16, 12, 16); outer = [ordered]@{ south = 'south'; east = 'east' } },
        @{ from = @(0, 4, 12);  to = @(4, 12, 16);  outer = [ordered]@{ south = 'south'; west = 'west' } }
    )
    foreach ($post in $fullPosts) {
        $faces = [ordered]@{
            north = (MetalFace $post.outer['north'])
            east  = (MetalFace $post.outer['east'])
            south = (MetalFace $post.outer['south'])
            west  = (MetalFace $post.outer['west'])
        }
        [void]$fullElements.Add((Element $post.from $post.to $faces))
    }
    # 玻璃墙在完整模型里也只占中间（y 4..12），六面全画
    $fullWalls = @(
        @{ from = @(4, 4, 0);   to = @(12, 12, 4);   outer = 'north' },
        @{ from = @(4, 4, 12);  to = @(12, 12, 16);  outer = 'south' },
        @{ from = @(0, 4, 4);   to = @(4, 12, 12);   outer = 'west' },
        @{ from = @(12, 4, 4);  to = @(16, 12, 12);  outer = 'east' }
    )
    foreach ($wall in $fullWalls) {
        $culls = [ordered]@{}
        $culls[$wall.outer] = $wall.outer
        $faces = [ordered]@{
            down  = (GlassFace $null)
            up    = (GlassFace $null)
            north = (GlassFace $culls['north'])
            south = (GlassFace $culls['south'])
            west  = (GlassFace $culls['west'])
            east  = (GlassFace $culls['east'])
        }
        [void]$fullElements.Add((Element $wall.from $wall.to $faces))
    }
    Write-Json (Join-Path $modelDir "$id.json") ([ordered]@{
        parent   = 'minecraft:block/block'
        textures = $texGlass
        elements = $fullElements
    })

    # ------------------------------------------------------------ 方块状态（multipart）
    $multipart = New-Object System.Collections.ArrayList
    [void]$multipart.Add([ordered]@{
        when  = [ordered]@{ bottom = 'true' }
        apply = [ordered]@{ model = "$NS/cap_bottom" }
    })
    [void]$multipart.Add([ordered]@{
        when  = [ordered]@{ top = 'true' }
        apply = [ordered]@{ model = "$NS/cap_top" }
    })
    # 26.x 的 when 只收 JSON 对象（多属性 = AND），所以把「甲外 || 乙外」拆成 3 个互斥组合
    $postWhen = [ordered]@{
        post_nw = @(
            [ordered]@{ north = 'false'; west = 'false' },
            [ordered]@{ north = 'false'; west = 'true' },
            [ordered]@{ north = 'true';  west = 'false' }
        )
        post_ne = @(
            [ordered]@{ north = 'false'; east = 'false' },
            [ordered]@{ north = 'false'; east = 'true' },
            [ordered]@{ north = 'true';  east = 'false' }
        )
        post_se = @(
            [ordered]@{ south = 'false'; east = 'false' },
            [ordered]@{ south = 'false'; east = 'true' },
            [ordered]@{ south = 'true';  east = 'false' }
        )
        post_sw = @(
            [ordered]@{ south = 'false'; west = 'false' },
            [ordered]@{ south = 'false'; west = 'true' },
            [ordered]@{ south = 'true';  west = 'false' }
        )
    }
    foreach ($name in @('post_nw', 'post_ne', 'post_se', 'post_sw')) {
        foreach ($when in $postWhen[$name]) {
            [void]$multipart.Add([ordered]@{
                when  = $when
                apply = [ordered]@{ model = "$NS/$name" }
            })
        }
    }
    foreach ($dirName in @('north', 'east', 'south', 'west')) {
        $whenGlass = [ordered]@{}
        $whenGlass[$dirName] = 'false'
        $whenGlass['window'] = 'true'
        [void]$multipart.Add([ordered]@{
            when  = $whenGlass
            apply = [ordered]@{ model = "$NS/wall_${dirName}_glass" }
        })
        $whenSolid = [ordered]@{}
        $whenSolid[$dirName] = 'false'
        $whenSolid['window'] = 'false'
        [void]$multipart.Add([ordered]@{
            when  = $whenSolid
            apply = [ordered]@{ model = "$NS/wall_${dirName}_solid" }
        })
    }
    Write-Json (Join-Path $stateDir "$id.json") ([ordered]@{ multipart = $multipart })

    # ------------------------------------------------------------ 物品模型
    Write-Json (Join-Path $itemDir "$id.json") ([ordered]@{
        model = [ordered]@{
            type  = 'minecraft:model'
            model = "stellar_evolution:block/$id"
        }
    })
    Write-Output "  models block/$id/*.json + block/$id.json, state $id.json, item $id.json"
}

Write-Output 'done.'

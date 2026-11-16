# gen_copper_materials.ps1 —— 补齐铜材质里「原版铜锭没有的 5 种形状」。
#
# 口径（2026-10-15 改版，与石化 / 生化 / 裂变线统一）：
#   **材料模板上色** —— 拿「浅色灰度模板」（`模板/材料/浅色/material_*.png`）逐像素上色。
#   与其它线的差别只在**取色方式**：这几件是**金属**，故用**原版铜锭自身的明暗色带**当色表，
#   而不是「基色 × 亮度」—— 这样金属的高光偏白、暗部偏褐的色相走向与铜锭完全一致。
#
#   做法：
#     1) 从原版 copper_ingot.png 取所有不透明像素，按亮度升序排成 256 级色带 LUT
#        （LUT[0] = 铜锭最暗的像素色，LUT[255] = 最亮的像素色）；
#     2) 灰度模板每个像素求亮度，按模板自身的最小/最大亮度归一化到 0..1，再查 LUT；
#     3) 保留原 alpha，输出到模组贴图目录 + 两份模型定义 JSON。
#
# 输出：src/main/resources/assets/stellar_evolution/textures/item/copper_{dust,plate,rod,wire,gear}.png
#       src/main/resources/assets/stellar_evolution/models/item/copper_*.json
#       src/main/resources/assets/stellar_evolution/items/copper_*.json
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_copper_materials.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ProjRoot   = Split-Path -Parent $PSScriptRoot
$TplLight   = Join-Path $ProjRoot '模板\材料\浅色'
$OutItem    = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\textures\item'
$ModelsDir  = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\models\item'
$ItemsDir   = Join-Path $ProjRoot 'src\main\resources\assets\stellar_evolution\items'
$VanillaJar = Join-Path $ProjRoot 'build\moddev\artifacts\minecraft-patched-26.1.2.114.jar'

# 原版铜已有 ingot / nugget / block，只补缺失的这 5 种（物品 id -> 材料模板形状）
$items = @(
    @{ id = 'copper_dust';  shape = 'dust'  },
    @{ id = 'copper_plate'; shape = 'plate' },
    @{ id = 'copper_rod';   shape = 'rod'   },
    @{ id = 'copper_wire';  shape = 'wire'  },
    @{ id = 'copper_gear';  shape = 'gear'  }
)

New-Item -ItemType Directory -Force -Path $OutItem, $ModelsDir, $ItemsDir | Out-Null

function Lum([System.Drawing.Color]$c) { return 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }

function Write-FlatItemModel([string]$name) {
    $model = @"
{
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "stellar_evolution:item/$name"
    }
}
"@
    Set-Content -Path (Join-Path $ModelsDir ("{0}.json" -f $name)) -Value $model -Encoding UTF8

    $def = @"
{
    "model": {
        "type": "minecraft:model",
        "model": "stellar_evolution:item/$name"
    }
}
"@
    Set-Content -Path (Join-Path $ItemsDir ("{0}.json" -f $name)) -Value $def -Encoding UTF8
}

# ---------- 1) 从原版铜锭提取 256 级明暗色带 ----------
$zip   = [System.IO.Compression.ZipFile]::OpenRead($VanillaJar)
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/minecraft/textures/item/copper_ingot.png' } | Select-Object -First 1
$ms = New-Object System.IO.MemoryStream
$es = $entry.Open(); $es.CopyTo($ms); $es.Close(); $zip.Dispose()
$copperBmp = [System.Drawing.Bitmap]::FromStream($ms)

$px = @()
for ($y = 0; $y -lt $copperBmp.Height; $y++) {
    for ($x = 0; $x -lt $copperBmp.Width; $x++) {
        $c = $copperBmp.GetPixel($x, $y)
        if ($c.A -eq 0) { continue }
        $px += [pscustomobject]@{ L = (Lum $c); R = $c.R; G = $c.G; B = $c.B }
    }
}
$copperBmp.Dispose()
$px = $px | Sort-Object -Property L
$n = $px.Count
$lut = @()
for ($i = 0; $i -lt 256; $i++) {
    $idx = [int][Math]::Round($i / 255.0 * ($n - 1))
    $lut += $px[$idx]
}
Write-Host ("原版铜锭：不透明像素 {0} 个，亮度 {1:N1} ~ {2:N1}" -f $n, $px[0].L, $px[$n - 1].L)
Write-Host ("  色带两端：最暗 RGB({0},{1},{2})  最亮 RGB({3},{4},{5})" -f $lut[0].R, $lut[0].G, $lut[0].B, $lut[255].R, $lut[255].G, $lut[255].B)

# ---------- 2) 逐张模板上色 + 写模型定义 ----------
foreach ($it in $items) {
    $src = Join-Path $TplLight ("material_{0}.png" -f $it.shape)
    if (-not (Test-Path $src)) { throw "缺少浅色模板：$src" }
    $bmp = [System.Drawing.Bitmap]::FromFile($src)

    # 模板自身的不透明像素亮度范围
    $minL = 255.0; $maxL = 0.0
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $l = Lum $c
            if ($l -lt $minL) { $minL = $l }
            if ($l -gt $maxL) { $maxL = $l }
        }
    }
    $span = [Math]::Max(1.0, $maxL - $minL)

    $out = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)); continue }
            $tNorm = ((Lum $c) - $minL) / $span
            $tNorm = [Math]::Min(1.0, [Math]::Max(0.0, $tNorm))
            $hit = $lut[[int][Math]::Round($tNorm * 255.0)]
            $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $hit.R, $hit.G, $hit.B))
        }
    }

    $dst = Join-Path $OutItem ("{0}.png" -f $it.id)
    $out.Save($dst, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("  {0,-14} <- 模板亮度 {1:N1} ~ {2:N1}  ->  {3}" -f $it.id, $minL, $maxL, (Split-Path -Leaf $dst))
    $out.Dispose(); $bmp.Dispose()

    Write-FlatItemModel $it.id
    Write-Host ("    {0}.json（models/item + items）" -f $it.id)
}

Write-Host ("完成：{0} 张铜材质已输出（模板上色 + 模型定义）" -f $items.Count)

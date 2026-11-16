﻿# gen_material_templates.ps1
# 从外部素材目录的灰度材料贴图生成模板：
#   深色：8 件贴图直接灰度化
#   浅色：以原版铜锭（copper_ingot）的平均亮度为基准，对深色集做统一的亮度偏移
# 输出：<项目根>/模板/材料/深色、<项目根>/模板/材料/浅色
#
# 用法：powershell -ExecutionPolicy Bypass -File tools/gen_material_templates.ps1

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'

$ProjRoot   = Split-Path -Parent $PSScriptRoot
$OutDark    = Join-Path $ProjRoot '模板\材料\深色'
$OutLight   = Join-Path $ProjRoot '模板\材料\浅色'
$SrcItem    = 'C:\MCmod项目\my_mod\src\main\resources\assets\m_ntm\textures\item'
$SrcBlock   = 'C:\MCmod项目\my_mod\src\main\resources\assets\m_ntm\textures\block'
$VanillaJar = 'C:\MCmod项目\stellar_evolution\build\moddev\artifacts\minecraft-patched-26.1.2.114.jar'

# 8 件来源贴图（7 物品 + 1 方块）；源文件名沿用外部素材，输出统一为 material_*
$files = @(
    @{ Dir = $SrcItem;  Source = 'titanium_gold_ingot.png';  Out = 'material_ingot.png'  },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_nugget.png'; Out = 'material_nugget.png' },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_dust.png';   Out = 'material_dust.png'   },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_plate.png';  Out = 'material_plate.png'  },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_rod.png';    Out = 'material_rod.png'    },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_wire.png';   Out = 'material_wire.png'   },
    @{ Dir = $SrcItem;  Source = 'titanium_gold_gear.png';   Out = 'material_gear.png'   },
    @{ Dir = $SrcBlock; Source = 'titanium_gold_block.png';  Out = 'material_block.png'  }
)

New-Item -ItemType Directory -Force -Path $OutDark  | Out-Null
New-Item -ItemType Directory -Force -Path $OutLight | Out-Null

function Get-MeanLum([System.Drawing.Bitmap]$bmp) {
    $sum = 0.0; $n = 0
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $sum += 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            $n++
        }
    }
    if ($n -eq 0) { return 0.0 }
    return $sum / $n
}

# ---------- 1) 原版铜锭参考亮度 ----------
$zip   = [System.IO.Compression.ZipFile]::OpenRead($VanillaJar)
$entry = $zip.Entries | Where-Object { $_.FullName -eq 'assets/minecraft/textures/item/copper_ingot.png' } | Select-Object -First 1
$ms = New-Object System.IO.MemoryStream
$es = $entry.Open(); $es.CopyTo($ms); $es.Close(); $zip.Dispose()
$copperBmp  = [System.Drawing.Bitmap]::FromStream($ms)
$copperMean = Get-MeanLum $copperBmp
$copperBmp.Dispose()
Write-Host ("参考：原版铜锭平均亮度 = {0:N2}" -f $copperMean)

# ---------- 2) 灰度化 -> 深色 ----------
$darkSet = @()
foreach ($f in $files) {
    $bmp = [System.Drawing.Bitmap]::FromFile((Join-Path $f.Dir $f.Source))
    $g = New-Object System.Drawing.Bitmap($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            $l = [int][Math]::Round(0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
            $g.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $l, $l, $l))
        }
    }
    $g.Save((Join-Path $OutDark $f.Out), [System.Drawing.Imaging.ImageFormat]::Png)
    $mean = Get-MeanLum $g
    $darkSet += [pscustomobject]@{ Name = $f.Out; Bmp = $g; Mean = $mean }
    $bmp.Dispose()
    Write-Host ("  深色 {0,-28} 平均亮度 {1:N2}" -f $f.Out, $mean)
}

# ---------- 3) 以铜锭亮度为基准做亮度偏移 -> 浅色 ----------
$darkMean = ($darkSet | Measure-Object -Property Mean -Average).Average
$delta    = $copperMean - $darkMean
Write-Host ("深色集平均亮度 = {0:N2}，亮度偏移 delta = {1:N2}" -f $darkMean, $delta)

foreach ($d in $darkSet) {
    $src = $d.Bmp
    $o = New-Object System.Drawing.Bitmap($src.Width, $src.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $c = $src.GetPixel($x, $y)
            $l = [int][Math]::Min(255.0, [Math]::Max(0.0, [Math]::Round($c.R + $delta)))
            $o.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $l, $l, $l))
        }
    }
    $o.Save((Join-Path $OutLight $d.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host ("  浅色 {0,-28} 平均亮度 {1:N2}" -f $d.Name, (Get-MeanLum $o))
    $o.Dispose()
    $src.Dispose()
}

Write-Host ("完成：{0} 件 -> 模板/材料/深色、模板/材料/浅色" -f $files.Count)

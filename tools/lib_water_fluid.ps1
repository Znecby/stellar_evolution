# lib_water_fluid.ps1 —— 统一流体贴图口径：取原版水的灰阶动画条，按目标基色换色。
#
# 原版 26.1.2 的资产（位于 build\moddev\artifacts\minecraft-patched-*-merged.jar）：
#   · water_still.png = 16×512（32 帧，mcmeta frametime=2），灰度 + alpha 180
#   · water_flow.png  = 32×1024（32 帧），灰度 + alpha 180
# 因此换色时只映射亮度、保留原始 alpha 与逐帧波纹，就能得到和原版水完全一致的
# 外观与动画，只是换成目标流体的颜色。以后新增流体一律走这个函数，不要再手画噪声/条纹。
#
# 用法（在 gen_*_materials.ps1 里）：
#   . (Join-Path $PSScriptRoot 'lib_water_fluid.ps1')
#   Write-WaterFluidTextures -BlockDir $BlockDir -Name 'crude_oil' -Rgb @(58, 47, 36)
#
# 说明：
#   · 亮度归一化后映射为 k = 0.55 + 0.90 * t（t=0 暗部 → 0.55×基色，t=1 高光 → 1.45×基色），
#     与原项目「材料模板上色」的口径一致；
#   · 输出仍为 32 帧动画条，并复制原版 .mcmeta，贴图会自动播放水波纹动画。

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-VanillaJarPath {
    param([string]$ProjRoot)
    $art = Join-Path $ProjRoot 'build\moddev\artifacts'
    $jar = Get-ChildItem $art -Filter 'minecraft-patched-*-merged.jar' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $jar) {
        $jar = Get-ChildItem $art -Filter 'minecraft-patched-*.jar' -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch 'sources' } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
    }
    if ($null -eq $jar) {
        throw '找不到 build\moddev\artifacts\minecraft-patched-*.jar（先跑一次 gradlew build）'
    }
    return $jar.FullName
}

function Read-JarEntryBytes {
    param([string]$Jar, [string]$Entry)
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
    try {
        $e = $zip.GetEntry($Entry)
        if ($null -eq $e) { throw "原版资产缺失：$Entry" }
        $ms = New-Object System.IO.MemoryStream
        $e.Open().CopyTo($ms)
        return ,$ms.ToArray()
    } finally {
        $zip.Dispose()
    }
}

function Convert-WaterStrip {
    param(
        [byte[]]$SourceBytes,
        [int[]]$Rgb,
        [string]$OutPath
    )
    Add-Type -AssemblyName System.Drawing
    $srcStream = New-Object System.IO.MemoryStream(,$SourceBytes)
    $bmp = [System.Drawing.Bitmap]::FromStream($srcStream)

    $rect = New-Object System.Drawing.Rectangle(0, 0, $bmp.Width, $bmp.Height)
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bytes = New-Object byte[] ($data.Stride * $bmp.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $bytes, 0, $bytes.Length)

    # 统计整条动画条的亮度范围（B,G,R,A 顺序）
    $minL = 255.0
    $maxL = 0.0
    for ($i = 0; $i -lt $bytes.Length; $i += 4) {
        if ($bytes[$i + 3] -eq 0) { continue }
        $l = 0.114 * $bytes[$i] + 0.587 * $bytes[$i + 1] + 0.299 * $bytes[$i + 2]
        if ($l -lt $minL) { $minL = $l }
        if ($l -gt $maxL) { $maxL = $l }
    }
    $span = [Math]::Max(1.0, $maxL - $minL)

    $r = [double]$Rgb[0]; $g = [double]$Rgb[1]; $b = [double]$Rgb[2]
    for ($i = 0; $i -lt $bytes.Length; $i += 4) {
        $a = $bytes[$i + 3]
        if ($a -eq 0) { continue }
        $l = 0.114 * $bytes[$i] + 0.587 * $bytes[$i + 1] + 0.299 * $bytes[$i + 2]
        $t = ($l - $minL) / $span
        $k = 0.55 + 0.90 * $t
        $bytes[$i]     = [byte][Math]::Max(0, [Math]::Min(255, [int][Math]::Round($b * $k)))
        $bytes[$i + 1] = [byte][Math]::Max(0, [Math]::Min(255, [int][Math]::Round($g * $k)))
        $bytes[$i + 2] = [byte][Math]::Max(0, [Math]::Min(255, [int][Math]::Round($r * $k)))
    }

    [System.Runtime.InteropServices.Marshal]::Copy($bytes, 0, $data.Scan0, $bytes.Length)
    $bmp.UnlockBits($data)
    $bmp.Save($OutPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $srcStream.Dispose()
}

function Write-WaterFluidTextures {
    param(
        [string]$BlockDir,
        [string]$Name,
        [int[]]$Rgb,
        [string]$ProjRoot = ''
    )
    if ([string]::IsNullOrWhiteSpace($ProjRoot)) { $ProjRoot = Split-Path -Parent $PSScriptRoot }
    $jar = Get-VanillaJarPath $ProjRoot
    New-Item -ItemType Directory -Force -Path $BlockDir | Out-Null

    $stillPng = Read-JarEntryBytes $jar 'assets/minecraft/textures/block/water_still.png'
    $flowPng  = Read-JarEntryBytes $jar 'assets/minecraft/textures/block/water_flow.png'
    $stillMeta = Read-JarEntryBytes $jar 'assets/minecraft/textures/block/water_still.png.mcmeta'
    $flowMeta  = Read-JarEntryBytes $jar 'assets/minecraft/textures/block/water_flow.png.mcmeta'

    Convert-WaterStrip -SourceBytes $stillPng -Rgb $Rgb -OutPath (Join-Path $BlockDir ("{0}_still.png" -f $Name))
    Convert-WaterStrip -SourceBytes $flowPng  -Rgb $Rgb -OutPath (Join-Path $BlockDir ("{0}_flow.png" -f $Name))
    [System.IO.File]::WriteAllBytes((Join-Path $BlockDir ("{0}_still.png.mcmeta" -f $Name)), $stillMeta)
    [System.IO.File]::WriteAllBytes((Join-Path $BlockDir ("{0}_flow.png.mcmeta" -f $Name)), $flowMeta)
    Write-Host ("  水换色流体 {0}_still/_flow.png（32 帧动画）" -f $Name)
}

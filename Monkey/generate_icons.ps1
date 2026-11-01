Add-Type -AssemblyName System.Drawing

$srcPath = "C:\Users\Akki\.gemini\antigravity-ide\brain\5bb13a2e-c12d-4aa1-9908-b5db67c343d4\.user_uploaded\media_1792959783326.jpg"
$baseDir = "F:\App project\RideAccepterPro\Spider\manager\src\main\res"

$densities = @(
    @{ name = "mipmap-mdpi";    launcherSize = 48;  fgSize = 108 },
    @{ name = "mipmap-hdpi";    launcherSize = 72;  fgSize = 162 },
    @{ name = "mipmap-xhdpi";   launcherSize = 96;  fgSize = 216 },
    @{ name = "mipmap-xxhdpi";  launcherSize = 144; fgSize = 324 },
    @{ name = "mipmap-xxxhdpi"; launcherSize = 192; fgSize = 432 }
)

$srcImg = [System.Drawing.Bitmap]::FromFile($srcPath)

function Resize-Image($source, $width, $height) {
    $destRect = New-Object System.Drawing.Rectangle(0, 0, $width, $height)
    $destImage = New-Object System.Drawing.Bitmap($width, $height)
    $destImage.SetResolution($source.HorizontalResolution, $source.VerticalResolution)

    $graphics = [System.Drawing.Graphics]::FromImage($destImage)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    $wrapMode = New-Object System.Drawing.Imaging.ImageAttributes
    $wrapMode.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)

    $graphics.DrawImage($source, $destRect, 0, 0, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel, $wrapMode)
    $graphics.Dispose()
    return $destImage
}

function Create-Round-Image($source, $size) {
    $destImage = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($destImage)
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size, $size)
    $graphics.SetClip($path)

    $destRect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $graphics.DrawImage($source, $destRect, 0, 0, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    return $destImage
}

function Create-Foreground-Image($source, $fgSize) {
    # 72dp of 108dp is safe zone (~66.7%). Let's scale logo to 72% centered on black/transparent canvas
    $innerSize = [int]($fgSize * 0.72)
    $offset = [int](($fgSize - $innerSize) / 2)

    $destImage = New-Object System.Drawing.Bitmap($fgSize, $fgSize)
    $graphics = [System.Drawing.Graphics]::FromImage($destImage)
    $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0)) # transparent

    $destRect = New-Object System.Drawing.Rectangle($offset, $offset, $innerSize, $innerSize)
    $graphics.DrawImage($source, $destRect, 0, 0, $source.Width, $source.Height, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.Dispose()
    return $destImage
}

function Create-Solid-Background($bgSize) {
    $destImage = New-Object System.Drawing.Bitmap($bgSize, $bgSize)
    $graphics = [System.Drawing.Graphics]::FromImage($destImage)
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 10, 10, 12)) # Deep sleek black
    $destRect = New-Object System.Drawing.Rectangle(0, 0, $bgSize, $bgSize)
    $graphics.FillRectangle($brush, $destRect)
    $brush.Dispose()
    $graphics.Dispose()
    return $destImage
}

foreach ($d in $densities) {
    $targetDir = Join-Path $baseDir $d.name
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }

    # 1. ic_launcher.png
    $launcherBmp = Resize-Image $srcImg $d.launcherSize $d.launcherSize
    $launcherBmp.Save((Join-Path $targetDir "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $launcherBmp.Dispose()

    # 2. ic_launcher_round.png
    $roundBmp = Create-Round-Image $srcImg $d.launcherSize
    $roundBmp.Save((Join-Path $targetDir "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $roundBmp.Dispose()

    # 3. ic_launcher_foreground.png
    $fgBmp = Create-Foreground-Image $srcImg $d.fgSize
    $fgBmp.Save((Join-Path $targetDir "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $fgBmp.Dispose()

    # 4. ic_launcher_background.png
    $bgBmp = Create-Solid-Background $d.fgSize
    $bgBmp.Save((Join-Path $targetDir "ic_launcher_background.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bgBmp.Dispose()

    Write-Host "Generated icons for $($d.name)"
}

$srcImg.Dispose()
Write-Host "All icons generated successfully!"

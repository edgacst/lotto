Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot "..\play-store\feature-graphic.png"
$textFile = Join-Path $PSScriptRoot "..\play-store\feature-graphic-text.json"
$w = 1024
$h = 500

$text = Get-Content -LiteralPath $textFile -Encoding UTF8 -Raw | ConvertFrom-Json

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

$g.Clear([System.Drawing.ColorTranslator]::FromHtml("#1B2F8F"))

$brushCircle1 = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#334CC4"))
$brushCircle2 = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#243CB0"))
$g.FillEllipse($brushCircle1, 680, -80, 440, 440)
$g.FillEllipse($brushCircle2, -120, 220, 400, 400)

$fontPath = "C:\Windows\Fonts\malgun.ttf"
$fontTitle = New-Object System.Drawing.Font ($fontPath, 52, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$fontSub = New-Object System.Drawing.Font ($fontPath, 68, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$fontCap = New-Object System.Drawing.Font ($fontPath, 28, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$fontNum = New-Object System.Drawing.Font ($fontPath, 26, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)

$brushGold = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#F4CD82"))
$brushWhite = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#FFFFFF"))
$brushCap = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#D8E2FF"))
$brushNum = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#151514"))

$g.DrawString($text.title, $fontTitle, $brushGold, 56, 118)
$g.DrawString($text.subtitle, $fontSub, $brushWhite, 56, 188)
$g.DrawString($text.caption, $fontCap, $brushCap, 56, 300)

$balls = @(
    @{ X = 760; Y = 150; Color = "#F5C84B"; Num = "6" },
    @{ X = 860; Y = 110; Color = "#FF7886"; Num = "12" },
    @{ X = 930; Y = 190; Color = "#69A9FF"; Num = "18" }
)
foreach ($ball in $balls) {
    $brushBall = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($ball.Color))
    $g.FillEllipse($brushBall, $ball.X, $ball.Y, 72, 72)
    $size = $g.MeasureString($ball.Num, $fontNum)
    $numX = $ball.X + (72 - $size.Width) / 2
    $numY = $ball.Y + (72 - $size.Height) / 2
    $g.DrawString($ball.Num, $fontNum, $brushNum, $numX, $numY)
}

$dir = Split-Path $out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

$fontTitle.Dispose()
$fontSub.Dispose()
$fontCap.Dispose()
$fontNum.Dispose()
$brushGold.Dispose()
$brushWhite.Dispose()
$brushCap.Dispose()
$brushNum.Dispose()
$brushCircle1.Dispose()
$brushCircle2.Dispose()
$g.Dispose()
$bmp.Dispose()

Write-Host "Saved $out"

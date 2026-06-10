Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot "..\play-store\feature-graphic.png"
$w = 1024
$h = 500

$bmp = New-Object System.Drawing.Bitmap $w, $h
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$bg = [System.Drawing.ColorTranslator]::FromHtml("#1B2F8F")
$g.Clear($bg)

$brushCircle1 = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#334CC4"))
$brushCircle2 = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#243CB0"))
$g.FillEllipse($brushCircle1, 680, -80, 440, 440)
$g.FillEllipse($brushCircle2, -120, 220, 400, 400)

$fontPath = "C:\Windows\Fonts\malgun.ttf"
$fontTitle = New-Object System.Drawing.Font ($fontPath, 54, [System.Drawing.FontStyle]::Regular)
$fontSub = New-Object System.Drawing.Font ($fontPath, 72, [System.Drawing.FontStyle]::Bold)
$fontCap = New-Object System.Drawing.Font ($fontPath, 30, [System.Drawing.FontStyle]::Regular)

$brushGold = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#F4CD82"))
$brushWhite = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#FFFFFF"))
$brushCap = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#D8E2FF"))

$g.DrawString("십이지신이 추천하는", $fontTitle, $brushGold, 56, 120)
$g.DrawString("행운의 로또번호", $fontSub, $brushWhite, 56, 190)
$g.DrawString("띠 운세 · QR 당첨조회 · 공식 통계", $fontCap, $brushCap, 56, 310)

$balls = @(
    @{ X = 760; Y = 150; Color = "#F5C84B"; Num = "6" },
    @{ X = 860; Y = 110; Color = "#FF7886"; Num = "12" },
    @{ X = 930; Y = 190; Color = "#69A9FF"; Num = "18" }
)
$brushNum = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml("#151514"))
foreach ($ball in $balls) {
    $brushBall = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($ball.Color))
    $g.FillEllipse($brushBall, $ball.X, $ball.Y, 72, 72)
    $g.DrawString($ball.Num, $fontCap, $brushNum, ($ball.X + 22), ($ball.Y + 18))
}

$dir = Split-Path $out -Parent
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)

$g.Dispose()
$bmp.Dispose()

Write-Host "Saved $out"

Add-Type -AssemblyName System.Drawing
$img = [System.Drawing.Image]::FromFile('d:\Project\LingShu-AI\frontend\public\logo.png')
Write-Host "Width: $($img.Width)px, Height: $($img.Height)px"
$img.Dispose()

Add-Type -AssemblyName System.Drawing
$files = Get-ChildItem -Path 'E:\*\mic-parent\mic\src\assets\img\*.jpg'
foreach ($f in $files) {
  $bmp = New-Object System.Drawing.Bitmap $f.FullName
  $rSum = 0.0; $gSum = 0.0; $bSum = 0.0; $n = 0
  $cols = 6; $rows = 4
  $grid = @()
  for ($ry = 0; $ry -lt $rows; $ry++) {
    $y0 = [int]($ry * $bmp.Height / $rows); $y1 = [int](($ry + 1) * $bmp.Height / $rows) - 1
    $line = ""
    for ($cx = 0; $cx -lt $cols; $cx++) {
      $x0 = [int]($cx * $bmp.Width / $cols); $x1 = [int](($cx + 1) * $bmp.Width / $cols) - 1
      $lSum = 0.0; $cnt = 0
      for ($y = $y0; $y -le $y1; $y += 6) {
        for ($x = $x0; $x -le $x1; $x += 6) {
          $c = $bmp.GetPixel($x, $y)
          $lSum += 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
          $cnt++
        }
      }
      $ml = 0.0
      if ($cnt -gt 0) { $ml = $lSum / $cnt }
      $line += ("{0,4:0} " -f $ml)
    }
    $grid += $line
  }
  for ($y = 0; $y -lt $bmp.Height; $y += 24) {
    for ($x = 0; $x -lt $bmp.Width; $x += 24) {
      $c = $bmp.GetPixel($x, $y)
      $rSum += $c.R; $gSum += $c.G; $bSum += $c.B; $n++
    }
  }
  $mr = [int]($rSum / $n); $mg = [int]($gSum / $n); $mb = [int]($bSum / $n)
  $mlum = [int](0.299 * $mr + 0.587 * $mg + 0.114 * $mb)
  Write-Output ("=== {0}  {1}x{2}  avgRGB({3},{4},{5}) lum={6}" -f $f.Name, $bmp.Width, $bmp.Height, $mr, $mg, $mb, $mlum)
  foreach ($line in $grid) { Write-Output ("    " + $line) }
  $bmp.Dispose()
}

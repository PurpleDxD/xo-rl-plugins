$changes = @{
    "com.example" = "net.runelite.client.plugins.ethan"
    "net.runelite.client.plugins.devtools" = "net.runelite.client.plugins.xo.devtools"
    'name = "EthanApiPlugin",' = 'name = "<html><font color=#42f551>[Ethan]</font> EthanApiPlugin</html>",'
    'name = "Packet Utils",' = 'name = "<html><font color=#42f551>[Ethan]</font> Packet Utils</html>",'
}

$javaFiles = Get-ChildItem -Path $PSScriptRoot -Filter *.java -Recurse

$allChanges = @()

foreach ($javaFile in $javaFiles) {
    $fileContent = Get-Content -Path $javaFile.FullName

    foreach ($oldPackage in $changes.Keys) {
        $newPackage = $changes[$oldPackage]

        $modifiedLines = $fileContent | ForEach-Object {
            $oldLine = $_
            $newLine = $_ -replace [regex]::Escape($oldPackage), $newPackage
            if ($newLine -ne $oldLine) {
                $changeInfo = New-Object PSObject -Property @{
                    File = $javaFile
                    LineNumber = [array]::IndexOf($fileContent, $_) + 1
                    OldLine = $oldLine
                    NewLine = $newLine
                }
                $changeInfo
            }
        }

        if ($modifiedLines) {
            $allChanges += $modifiedLines
        }
    }
}

$allChanges | Format-Table File, LineNumber, OldLine, NewLine -AutoSize

if (!$allChanges) {
    return;
}

$confirmation = Read-Host "Do you want to apply these changes? (Y/N)"

if ($confirmation -eq 'Y' -or $confirmation -eq 'y') {
    foreach ($change in $allChanges) {
        $fileContent = Get-Content -Path $change.File.FullName

        $fileContent[$change.LineNumber - 1] = $change.NewLine

        Set-Content -Path $change.File.FullName -Value $fileContent
    }

    Write-Host "All changes applied successfully."
} else {
    Write-Host "Changes skipped."
}
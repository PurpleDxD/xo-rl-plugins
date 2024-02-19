function Perform-SparseCheckout {
    param(
        [string]$SourcePath,
        [string]$SourceToKeep
    )
    
    $sparseCheckoutFile = Join-Path -Path $PWD -ChildPath ".git\info\sparse-checkout"

    git config core.sparseCheckout true
    Set-Content -Path $sparseCheckoutFile -Value $SourcePath
    git checkout

    Copy-Item -Path $SourceToKeep/* -Destination $PWD -Recurse -Force
}

function Delete-Sources {
    param(
        [string[]]$ItemsToRemove
    )

    foreach ($item in $ItemsToRemove) {
        Remove-Item (Join-Path -Path $PWD -ChildPath $item) -Recurse -Force
    }
}

$repoDirectory = Join-Path -Path $PSScriptRoot -ChildPath "ethan"
$sourceDirectory = Join-Path -Path $repoDirectory -ChildPath "src"
$sourceToKeep = Join-Path -Path $repoDirectory -ChildPath "src/main/java/com/example"

Remove-Item -Path $repoDirectory -Recurse -Force -ErrorAction SilentlyContinue > $null 2>&1
git clone --no-checkout "https://github.com/Ethan-Vann/EthanVannPlugins" $repoDirectory > $null 2>&1

Push-Location $repoDirectory > $null 2>&1

Perform-SparseCheckout -SourcePath "src/main/java/com/example/*" -SourceToKeep $sourceToKeep > $null 2>&1
Delete-Sources -ItemsToRemove "Main.java", "PathingTesting", ".git" > $null 2>&1
Remove-Item -Path $sourceDirectory -Recurse -Force > $null 2>&1

Pop-Location > $null 2>&1
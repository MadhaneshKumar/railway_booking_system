# deploy-backend.ps1  -  recreate the railway-api Container App from the image already in ACR.
# Run in Windows PowerShell:   powershell -ExecutionPolicy Bypass -File .\deploy-backend.ps1
# Secrets are typed at the prompts (hidden) and are never written to disk.

$rg      = 'railway-rg'
$env_    = 'railway-env'
$acr     = 'railwayacr49134'
$storAcc = 'railwaystore49134'
$app     = 'railway-api'
$tag     = 'v1'

function Fail($msg) { Write-Host "`nSTOP: $msg" -ForegroundColor Red; exit 1 }
function Plain($secure) {
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringAuto($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
}
function HasBadChars($s) { return ($s -match '[&|<>^%"\s]') }

Write-Host "== 1/6 Azure login check ==" -ForegroundColor Cyan
$acct = az account show --query name -o tsv 2>$null
if (-not $acct) { Write-Host "Not signed in. Opening az login..."; az login | Out-Null; $acct = az account show --query name -o tsv }
if (-not $acct) { Fail "az login did not complete." }
Write-Host "Subscription: $acct"

Write-Host "`n== 2/6 Checking existing Azure resources ==" -ForegroundColor Cyan
$tags = az acr repository show-tags --name $acr --repository railway-api -o tsv 2>$null
if ($tags -notcontains $tag) { Fail "Image railway-api:$tag is not in $acr. Push it again first (docker push)." }
Write-Host "Image $acr.azurecr.io/railway-api:$tag found."
$envState = az containerapp env show --name $env_ --resource-group $rg --query properties.provisioningState -o tsv 2>$null
if ($envState -ne 'Succeeded') { Fail "Container Apps environment '$env_' is not ready (state: '$envState')." }
Write-Host "Environment $env_ ready."

Write-Host "`n== 3/6 Enter your settings ==" -ForegroundColor Cyan
Write-Host "Atlas URI, like mongodb+srv://USER:PASSWORD@HOST.mongodb.net/railway  (anything after ? is removed)"
$mongo = Plain (Read-Host "MongoDB Atlas connection string" -AsSecureString)
$mongo = ($mongo.Trim() -split '\?')[0]
if ($mongo -notmatch '^mongodb(\+srv)?://') { Fail "That does not look like a MongoDB URI." }
if ($mongo -notmatch '/[^/@]+$' -or $mongo -notmatch '@[^/]+/[A-Za-z0-9_-]+$') { Fail "The URI must end with the database name, e.g. .../railway" }
if (HasBadChars $mongo) { Fail "Mongo URI contains a character that breaks az on Windows (& | < > ^ % space or a quote)." }

$redisHost = Read-Host "Upstash Redis host (e.g. robust-unicorn-220650.upstash.io)"
$redisHost = $redisHost.Trim()
if (-not $redisHost -or (HasBadChars $redisHost)) { Fail "Redis host is empty or has bad characters." }
$redisPort = Read-Host "Upstash Redis port (press Enter for 6379)"
if (-not $redisPort) { $redisPort = '6379' }
$redisPass = (Plain (Read-Host "Upstash Redis password/token" -AsSecureString)).Trim()
if (-not $redisPass -or (HasBadChars $redisPass)) { Fail "Redis password is empty or has bad characters." }

$chars = [char[]]'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
$rng = New-Object Security.Cryptography.RNGCryptoServiceProvider
$bytes = New-Object byte[] 48
$rng.GetBytes($bytes)
$auth = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

$storage = az storage account show-connection-string --name $storAcc --resource-group $rg --query connectionString -o tsv
if (-not $storage) { Fail "Could not read the storage connection string." }
if ($storage -match '[&|<>^%"\s]') { Fail "Storage connection string has a bad character." }
$acrPass = az acr credential show --name $acr --query "passwords[0].value" -o tsv
if (-not $acrPass) { Fail "Could not read ACR credentials (is the admin user enabled?)." }
Write-Host "All values collected."

Write-Host "`n== 4/6 Removing any old app ==" -ForegroundColor Cyan
$existing = az containerapp list --resource-group $rg --query "[?name=='$app'].name" -o tsv
if ($existing) {
    Write-Host "Deleting old '$app' (1-3 minutes, do not press Ctrl+C)..."
    az containerapp delete --name $app --resource-group $rg --yes | Out-Null
    for ($i = 0; $i -lt 30; $i++) {
        $still = az containerapp list --resource-group $rg --query "[?name=='$app'].name" -o tsv
        if (-not $still) { break }
        Start-Sleep 10
    }
    if ($still) { Fail "Old app is still being deleted. Wait a few minutes and run the script again." }
}
Write-Host "No old app present."

Write-Host "`n== 5/6 Creating the app ==" -ForegroundColor Cyan
az containerapp create --name $app --resource-group $rg --environment $env_ `
  --image "$acr.azurecr.io/railway-api:$tag" `
  --registry-server "$acr.azurecr.io" --registry-username $acr --registry-password $acrPass `
  --ingress external --target-port 8080 --cpu 1 --memory 2Gi --min-replicas 1 `
  --secrets "mongo-uri=$mongo" "redis-pass=$redisPass" "auth-secret=$auth" "storage-conn=$storage" `
  --env-vars PAYMENT_MOCK=true SERVER_FORWARD_HEADERS_STRATEGY=native REDIS_SSL=true "REDIS_HOST=$redisHost" "REDIS_PORT=$redisPort" `
             MONGODB_URI=secretref:mongo-uri REDIS_PASSWORD=secretref:redis-pass AUTH_TOKEN_SECRET=secretref:auth-secret AZURE_STORAGE_CONNECTION_STRING=secretref:storage-conn `
  --query "{fqdn:properties.configuration.ingress.fqdn,state:properties.provisioningState}" -o table
if ($LASTEXITCODE -ne 0) { Fail "az containerapp create failed (see message above)." }

$secretNames = az containerapp show --name $app --resource-group $rg --query "properties.configuration.secrets[].name" -o tsv
Write-Host ("Secrets on the app: " + ($secretNames -join ', '))
foreach ($n in 'mongo-uri','redis-pass','auth-secret','storage-conn') {
    if ($secretNames -notcontains $n) { Fail "Secret '$n' is missing on the new app." }
}

Write-Host "`n== 6/6 Waiting for /actuator/health (up to 6 minutes) ==" -ForegroundColor Cyan
$api = az containerapp show --name $app --resource-group $rg --query properties.configuration.ingress.fqdn -o tsv
$up = $false
for ($i = 1; $i -le 24; $i++) {
    try {
        $r = Invoke-RestMethod "https://$api/actuator/health" -TimeoutSec 15
        if ($r.status -eq 'UP') { $up = $true; break }
        Write-Host "  attempt $i : status = $($r.status)"
    } catch {
        Write-Host "  attempt $i : not ready yet"
    }
    Start-Sleep 15
}
if ($up) {
    Write-Host "`nBACKEND IS UP:  https://$api" -ForegroundColor Green
    Write-Host "Save this address. The frontend build needs it as VITE_API_URL."
} else {
    Write-Host "`nStill not healthy after 6 minutes." -ForegroundColor Yellow
    Write-Host "Read the newest logs and paste them to Claude:"
    Write-Host '  $wsid = az monitor log-analytics workspace show --resource-group railway-rg --workspace-name workspace-railwayrgyI26 --query customerId -o tsv'
    Write-Host "  az monitor log-analytics query --workspace `$wsid --analytics-query `"ContainerAppConsoleLogs_CL | where ContainerAppName_s == 'railway-api' | where TimeGenerated > ago(8m) | where Log_s has_any ('Started RailwayApplication','AUTH_TOKEN_SECRET','mongodb.net','localhost:27017','upstash','Caused by') | order by TimeGenerated desc | project TimeGenerated, L=substring(Log_s,0,220) | take 12`" -o tsv"
}

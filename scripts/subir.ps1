<#
.SYNOPSIS
    Sobe o Framework de Redes localmente, sempre via Docker.

.DESCRIPTION
    PROPOSITO: ter UM jeito de rodar o projeto, igual ao de producao. Rodar
    `gradlew quarkusDev` na maquina usa outro caminho — sem Redis, sem as
    variaveis do container, com outro perfil — e esconde justamente os defeitos
    que so aparecem em producao. Este projeto ja pagou duas vezes por isso: a
    variavel do OAuth que nao chegava ao container, e a telemetria sem Redis.

    INVARIANTES: usa a mesma imagem e o mesmo compose que a producao usa; nunca
    inventa segredo de producao (os defaults de dev sao explicitamente de dev);
    espera o healthcheck antes de dizer que subiu.

    COMPORTAMENTO EM CASO DE FALHA: se o Docker nao estiver rodando, avisa e
    para — nao tenta caminho alternativo, porque cair para `quarkusDev` em
    silencio seria voltar ao problema que este script existe para resolver. Se
    o healthcheck nao passar no prazo, mostra os ultimos logs e sai com codigo 1.

.PARAMETER Porta
    Porta no host. Padrao 8081.

.PARAMETER SemBrowser
    Nao abre o navegador ao final.

.PARAMETER Recriar
    Reconstroi a imagem do zero (--no-cache) e recria os containers.

.EXAMPLE
    .\scripts\subir.ps1
    .\scripts\subir.ps1 -Porta 8090 -Recriar
#>
[CmdletBinding()]
param(
    [int]$Porta = 8081,
    [switch]$SemBrowser,
    [switch]$Recriar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$compose = 'docker-compose.dev.yml'
$env:HTTP_PORT = "$Porta"

Write-Host ''
Write-Host '  Framework de Redes — subindo via Docker' -ForegroundColor Cyan
Write-Host '  ---------------------------------------' -ForegroundColor DarkGray

# --- 1. Docker esta de pe? -------------------------------------------------
try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'daemon nao respondeu' }
} catch {
    Write-Host ''
    Write-Host '  Docker nao esta rodando.' -ForegroundColor Red
    Write-Host '  Abra o Docker Desktop, espere ficar verde e rode de novo.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Nao vou cair para gradlew quarkusDev: ambiente diferente do de' -ForegroundColor DarkGray
    Write-Host '  producao esconde defeito, e e por isso que este script existe.' -ForegroundColor DarkGray
    exit 1
}
Write-Host '  [ok] Docker respondendo' -ForegroundColor Green

# --- 2. Subir --------------------------------------------------------------
if ($Recriar) {
    Write-Host '  [..] Reconstruindo do zero (--no-cache)' -ForegroundColor DarkGray
    docker compose -f $compose build --no-cache
    if ($LASTEXITCODE -ne 0) { Write-Host '  Falha no build.' -ForegroundColor Red; exit 1 }
    docker compose -f $compose up -d --force-recreate
} else {
    Write-Host '  [..] Construindo e subindo' -ForegroundColor DarkGray
    docker compose -f $compose up -d --build
}
if ($LASTEXITCODE -ne 0) { Write-Host '  Falha ao subir os containers.' -ForegroundColor Red; exit 1 }

# --- 3. Esperar o healthcheck ---------------------------------------------
Write-Host '  [..] Aguardando a aplicacao responder' -ForegroundColor DarkGray
$url = "http://127.0.0.1:$Porta/"
$limite = (Get-Date).AddSeconds(150)
$pronto = $false

while ((Get-Date) -lt $limite) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $pronto = $true; break }
    } catch {
        Start-Sleep -Seconds 3
    }
}

if (-not $pronto) {
    Write-Host ''
    Write-Host '  A aplicacao nao respondeu no prazo. Ultimos logs:' -ForegroundColor Red
    docker compose -f $compose logs --tail=40 framework-net
    exit 1
}

# --- 4. Pronto -------------------------------------------------------------
Write-Host '  [ok] Aplicacao no ar' -ForegroundColor Green
Write-Host ''
docker compose -f $compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'
Write-Host ''
Write-Host "  Aplicacao ....... $url" -ForegroundColor Cyan
Write-Host "  Telemetria ...... ${url}telemetria  (login: contingencia, chave dev-admin-key-local)" -ForegroundColor Cyan
Write-Host ''
Write-Host '  Parar ........... .\scripts\parar.ps1' -ForegroundColor DarkGray
Write-Host '  Logs ao vivo .... docker compose -f docker-compose.dev.yml logs -f framework-net' -ForegroundColor DarkGray
Write-Host ''

if (-not $SemBrowser) {
    Start-Process $url
}

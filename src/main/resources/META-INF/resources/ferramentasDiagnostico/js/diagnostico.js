function executarPing() {
    const host = document.getElementById('pingHost').value;
    const btn = document.getElementById('btnPing');
    const resultElement = document.getElementById('pingResult');
    const terminalElement = document.getElementById('pingTerminal');
    
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Processando...';
    
    // Mostra terminal com loading
    terminalElement.classList.remove('d-none');
    resultElement.textContent = "Disparando ping para " + host + "...\n";

    // Simula uma demora realista do "ping"
    setTimeout(() => {
        fetch('/diagnostico/api/ping', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'CSRF-Token': getCsrfToken()
            },
            body: new URLSearchParams({ host: host })
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => { throw new Error(text) });
            }
            return response.json();
        })
        .then(data => {
            resultElement.textContent = data.data; // Sucesso
        })
        .catch(error => {
            resultElement.textContent = "Erro: " + error.message;
            resultElement.style.color = "#ff5252";
        })
        .finally(() => {
            btn.disabled = false;
            btn.innerHTML = 'Disparar Ping Simulado';
        });
    }, 1500);
}

function executarDns() {
    const dominio = document.getElementById('dnsDominio').value;
    const btn = document.getElementById('btnDns');
    const resultElement = document.getElementById('dnsResult');
    const terminalElement = document.getElementById('dnsTerminal');
    
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Resolvendo...';
    
    terminalElement.classList.remove('d-none');
    resultElement.textContent = "Consultando root servers para " + dominio + "...\n";

    setTimeout(() => {
        fetch('/diagnostico/api/dns', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'CSRF-Token': getCsrfToken()
            },
            body: new URLSearchParams({ dominio: dominio })
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => { throw new Error(text) });
            }
            return response.json();
        })
        .then(data => {
            resultElement.textContent = data.data;
        })
        .catch(error => {
            resultElement.textContent = "Erro: " + error.message;
            resultElement.style.color = "#ff5252";
        })
        .finally(() => {
            btn.disabled = false;
            btn.innerHTML = 'Resolver Nome Simulado';
        });
    }, 1000);
}

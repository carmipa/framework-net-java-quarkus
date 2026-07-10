function testarPacote() {
    const regra = document.getElementById('aclRegra').value;
    const ipOrigem = document.getElementById('ipOrigem').value;
    const ipDestino = document.getElementById('ipDestino').value;
    const portaDestino = document.getElementById('portaDestino').value;
    const btn = document.getElementById('btnTestar');
    
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Avaliando...';
    
    document.getElementById('placeholderResultado').classList.add('d-none');
    
    // Oculta enquanto processa
    const ativo = document.getElementById('resultadoAtivo');
    ativo.classList.add('d-none');

    setTimeout(() => {
        fetch('/seguranca/api/testar', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'CSRF-Token': getCsrfToken()
            },
            body: new URLSearchParams({ 
                regra: regra,
                ipOrigem: ipOrigem,
                ipDestino: ipDestino,
                portaDestino: portaDestino
            })
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => { throw new Error(text) });
            }
            return response.json();
        })
        .then(data => {
            exibirResultado(data.data, ipOrigem, ipDestino, portaDestino);
        })
        .catch(error => {
            exibirErro("Erro: " + error.message);
        })
        .finally(() => {
            btn.disabled = false;
            btn.innerHTML = 'Testar Regra (MATCH / DENY)';
        });
    }, 800);
}

function exibirResultado(resultadoServidor, origem, destino, porta) {
    const ativo = document.getElementById('resultadoAtivo');
    const icone = document.getElementById('iconeResultado');
    const titulo = document.getElementById('textoResultadoPrincipal');
    const explicacao = document.getElementById('textoExplicacao');
    const detalhe = document.getElementById('detalhePacote');
    
    ativo.classList.remove('d-none');
    detalhe.textContent = `Origem: ${origem} -> Destino: ${destino}:${porta}`;
    explicacao.textContent = resultadoServidor;
    
    if (resultadoServidor.includes('PERMITIDO')) {
        icone.innerHTML = '<span class="material-symbols-outlined icon-animated resultado-permit">check_circle</span>';
        titulo.textContent = 'MATCH - PERMITIDO';
        titulo.className = 'mb-3 resultado-permit';
    } else if (resultadoServidor.includes('BLOQUEADO')) {
        icone.innerHTML = '<span class="material-symbols-outlined icon-animated resultado-deny">cancel</span>';
        titulo.textContent = 'MATCH - BLOQUEADO';
        titulo.className = 'mb-3 resultado-deny';
    } else {
        icone.innerHTML = '<span class="material-symbols-outlined icon-animated resultado-nomatch">help_center</span>';
        titulo.textContent = 'NO MATCH';
        titulo.className = 'mb-3 resultado-nomatch';
    }
}

function exibirErro(msg) {
    const ativo = document.getElementById('resultadoAtivo');
    const icone = document.getElementById('iconeResultado');
    const titulo = document.getElementById('textoResultadoPrincipal');
    const explicacao = document.getElementById('textoExplicacao');
    
    ativo.classList.remove('d-none');
    document.getElementById('detalhePacote').textContent = '';
    
    icone.innerHTML = '<span class="material-symbols-outlined icon-animated text-danger">error</span>';
    titulo.textContent = 'ERRO';
    titulo.className = 'mb-3 text-danger';
    explicacao.textContent = msg;
}

/**
 * Progresso das lições guardado no próprio navegador (antes de existir conta no site).
 *
 * PROPÓSITO DE NEGÓCIO: o aluno vê o que já fez — acertos, tentativas e lições concluídas — sem
 *   precisar de login. E vê, sempre, ONDE isso está guardado: só neste navegador. Computador de
 *   laboratório que limpa tudo ao desligar apaga o progresso, e o aluno precisa saber disso antes,
 *   não depois.
 *
 * INVARIANTES DO DOMÍNIO:
 *   - uma chave por lição ("academia.progresso.v1.<licaoId>"), com estado absoluto
 *     { acertos, tentativas, concluidaEm } — nunca incremento cego vindo de fora;
 *   - tentativas >= acertos >= 0, inteiros;
 *   - a lição conclui ao chegar a ACERTOS_PARA_CONCLUIR acertos, e concluidaEm é gravado uma vez;
 *   - nada pessoal é guardado (nem o que foi digitado), só contagens;
 *   - a semente da pergunta vive em sessionStorage, para o reload da tradução não trocar a pergunta.
 *
 * COMPORTAMENTO EM CASO DE FALHA: armazenamento bloqueado ou cheio (navegação privada, política
 *   da escola) não quebra a lição: ler devolve o estado vazio, gravar devolve false, e o selo diz
 *   "o progresso não pode ser guardado neste navegador". Valor corrompido na chave é ignorado e
 *   tratado como estado vazio.
 */
(function (raiz) {
    'use strict';

    var PREFIXO = 'academia.progresso.v1.';
    var PREFIXO_SEMENTE = 'academia.semente.v1.';
    var ACERTOS_PARA_CONCLUIR = 3;

    function vazio() {
        return { acertos: 0, tentativas: 0, concluidaEm: null };
    }

    function armazenamento(tipo) {
        try {
            var s = raiz[tipo];
            var teste = '__academia_teste__';
            s.setItem(teste, '1');
            s.removeItem(teste);
            return s;
        } catch (e) {
            return null;
        }
    }

    function valido(estado) {
        return estado && Number.isInteger(estado.acertos) && Number.isInteger(estado.tentativas)
            && estado.acertos >= 0 && estado.tentativas >= estado.acertos
            && (estado.concluidaEm === null || typeof estado.concluidaEm === 'string');
    }

    function ler(licaoId) {
        var s = armazenamento('localStorage');
        if (!s) {
            return vazio();
        }
        try {
            var bruto = s.getItem(PREFIXO + licaoId);
            var estado = bruto ? JSON.parse(bruto) : null;
            return valido(estado) ? estado : vazio();
        } catch (e) {
            return vazio();
        }
    }

    function gravar(licaoId, estado) {
        var s = armazenamento('localStorage');
        if (!s || !valido(estado)) {
            return false;
        }
        try {
            s.setItem(PREFIXO + licaoId, JSON.stringify(estado));
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Conta uma tentativa (já validada pelo normalizador) e devolve o novo estado, com
     * `acabouDeConcluir` true só na tentativa que concluiu a lição.
     */
    function registrarTentativa(licaoId, acertou) {
        var estado = ler(licaoId);
        estado.tentativas += 1;
        if (acertou) {
            estado.acertos += 1;
        }
        var acabouDeConcluir = false;
        if (!estado.concluidaEm && estado.acertos >= ACERTOS_PARA_CONCLUIR) {
            estado.concluidaEm = new Date().toISOString();
            acabouDeConcluir = true;
        }
        var guardado = gravar(licaoId, estado);
        return { estado: estado, guardado: guardado, acabouDeConcluir: acabouDeConcluir };
    }

    function disponivel() {
        return armazenamento('localStorage') !== null;
    }

    /** Ids das lições com progresso guardado neste navegador. */
    function licoesGuardadas() {
        var s = armazenamento('localStorage');
        var ids = [];
        if (!s) {
            return ids;
        }
        for (var i = 0; i < s.length; i++) {
            var chave = s.key(i);
            if (chave && chave.indexOf(PREFIXO) === 0) {
                ids.push(chave.slice(PREFIXO.length));
            }
        }
        return ids;
    }

    /** Apaga o progresso de todas as lições deste navegador; devolve quantas foram apagadas. */
    function apagarTudo() {
        var s = armazenamento('localStorage');
        if (!s) {
            return 0;
        }
        var ids = licoesGuardadas();
        ids.forEach(function (id) {
            try { s.removeItem(PREFIXO + id); } catch (e) { /* segue com as outras */ }
        });
        return ids.length;
    }

    /** Semente da pergunta atual da lição; nova a cada avanço, estável no reload. */
    function semente(licaoId) {
        var s = armazenamento('sessionStorage');
        var chave = PREFIXO_SEMENTE + licaoId;
        var atual = s ? parseInt(s.getItem(chave) || '', 10) : NaN;
        if (!Number.isInteger(atual) || atual <= 0) {
            atual = Math.floor(Math.random() * 2147483646) + 1;
            if (s) {
                try { s.setItem(chave, String(atual)); } catch (e) { /* sem sessão: semente só desta página */ }
            }
        }
        return atual;
    }

    function avancarSemente(licaoId) {
        var s = armazenamento('sessionStorage');
        var proxima = (semente(licaoId) % 2147483646) + 1;
        if (s) {
            try { s.setItem(PREFIXO_SEMENTE + licaoId, String(proxima)); } catch (e) { /* idem */ }
        }
        return proxima;
    }

    /** Preenche o selo de uma lição: concluída/andamento e onde está guardado. */
    function pintarSelo(el, licaoId) {
        if (!el) {
            return;
        }
        el.textContent = '';
        var icone = document.createElement('span');
        icone.className = 'material-symbols-outlined';
        icone.setAttribute('aria-hidden', 'true');
        icone.setAttribute('translate', 'no');
        var texto = document.createElement('span');
        if (!disponivel()) {
            icone.textContent = 'block';
            texto.textContent = 'o progresso não pode ser guardado neste navegador';
            el.classList.remove('concluida');
        } else {
            var estado = ler(licaoId);
            if (estado.concluidaEm) {
                icone.textContent = 'task_alt';
                texto.textContent = 'concluída — salvo só neste navegador';
                el.classList.add('concluida');
            } else if (estado.tentativas > 0) {
                icone.textContent = 'pending';
                texto.textContent = estado.acertos + ' de ' + ACERTOS_PARA_CONCLUIR + ' acertos — salvo só neste navegador';
                el.classList.remove('concluida');
            } else {
                icone.textContent = 'radio_button_unchecked';
                texto.textContent = 'não iniciada';
                el.classList.remove('concluida');
            }
        }
        el.appendChild(icone);
        el.appendChild(texto);
    }

    raiz.AcademiaProgresso = {
        ACERTOS_PARA_CONCLUIR: ACERTOS_PARA_CONCLUIR,
        ler: ler,
        registrarTentativa: registrarTentativa,
        disponivel: disponivel,
        licoesGuardadas: licoesGuardadas,
        apagarTudo: apagarTudo,
        semente: semente,
        avancarSemente: avancarSemente,
        pintarSelo: pintarSelo
    };
}(window));

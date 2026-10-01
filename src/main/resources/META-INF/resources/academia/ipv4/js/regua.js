/**
 * A régua de 32 bits do nível IPv4: cada bit do endereço, colorido como parte de rede ou de host.
 *
 * PROPÓSITO DE NEGÓCIO: a máscara é uma divisória dentro dos 32 bits. Ver a divisória andar — e
 *   poder movê-la com um clique — é o que faz "/27" deixar de ser um número decorado.
 *
 * INVARIANTES DO DOMÍNIO (INV-ACAD-007):
 *   - cada bit é um <button>; na régua interativa, clicar no bit N põe a divisória depois dele
 *     (prefixo /N), com rótulo acessível dizendo isso;
 *   - os valores vão em translate="no"; nada é desenhado em canvas;
 *   - a régua só mostra — quem calcula é o AcademiaIpv4.
 *
 * COMPORTAMENTO EM CASO DE FALHA: container ausente devolve uma régua inerte em vez de lançar.
 */
(function (raiz) {
    'use strict';

    function montar(container, opcoes) {
        var interativa = Boolean(opcoes && opcoes.interativa);
        var aoEscolher = (opcoes && opcoes.aoEscolherPrefixo) || function () {};
        var botoes = [];
        if (!container) {
            return { mostrar: function () {} };
        }
        container.textContent = '';
        for (var i = 0; i < 32; i++) {
            var botao = document.createElement('button');
            botao.type = 'button';
            botao.className = 'acad-regua-bit';
            botao.setAttribute('translate', 'no');
            botao.textContent = '0';
            if (interativa) {
                (function (prefixo) {
                    botao.title = 'Põe a divisória da máscara depois deste bit: /' + prefixo;
                    botao.addEventListener('click', function () { aoEscolher(prefixo); });
                }(i + 1));
            } else {
                botao.disabled = true;
            }
            botoes.push(botao);
            container.appendChild(botao);
        }

        /** Mostra os bits do endereço e a divisória do prefixo. */
        function mostrar(bitsEndereco, prefixo) {
            botoes.forEach(function (botao, i) {
                var rede = i < prefixo;
                botao.textContent = String(bitsEndereco[i]);
                botao.classList.toggle('rede', rede);
                botao.classList.toggle('host', !rede);
                botao.setAttribute('aria-label', 'bit ' + (i + 1) + ' vale ' + bitsEndereco[i] + ', parte de '
                    + (rede ? 'rede' : 'host') + (interativa ? '; ative para usar o prefixo /' + (i + 1) : ''));
            });
        }

        return { mostrar: mostrar };
    }

    raiz.AcademiaRegua = { montar: montar };
}(window));

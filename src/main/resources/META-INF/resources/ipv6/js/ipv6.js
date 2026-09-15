/*
 * Calculadora IPv6 — comportamento visual mínimo.
 * O cálculo acontece no servidor e volta como fragmento trocado pelo htmx; aqui só o botão Limpar.
 */
(function () {
    "use strict";

    function limparFormulario(botao) {
        var form = botao.closest("form");
        if (!form) {
            return;
        }
        form.querySelectorAll("input[type='text']").forEach(function (campo) {
            campo.value = "";
        });
        var alvo = form.getAttribute("hx-target");
        if (alvo) {
            var saida = document.querySelector(alvo);
            if (saida) {
                saida.innerHTML =
                    '<div class="calc-placeholder"><span class="material-symbols-outlined" aria-hidden="true">' +
                    'backspace</span><p class="mb-0">Campos limpos. Informe novos valores e calcule.</p></div>';
            }
        }
        var primeiro = form.querySelector("input[type='text']");
        if (primeiro) {
            primeiro.focus();
        }
    }

    document.addEventListener("click", function (evento) {
        var botao = evento.target.closest("[data-limpar]");
        if (botao) {
            limparFormulario(botao);
        }
    });
})();

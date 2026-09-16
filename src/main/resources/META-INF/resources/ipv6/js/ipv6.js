/*
 * Calculadora IPv6 — comportamento visual do módulo (próprio da página, desacoplado).
 * O cálculo acontece no servidor e volta como fragmento trocado pelo htmx; aqui ficam só a
 * navegação por abas (mesmo padrão da Análise IPv4) e o botão Limpar.
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

    function bootAbas() {
        var root = document.querySelector(".ipv6-wrap");
        if (!root) {
            return;
        }
        var gatilhos = root.querySelectorAll(".tab-trigger");
        var paineis = root.querySelectorAll(".tab-panel");

        function mostrar(id) {
            gatilhos.forEach(function (g) {
                g.classList.toggle("active", g.dataset.tab === id);
            });
            paineis.forEach(function (p) {
                p.classList.toggle("active", p.dataset.tabPanel === id);
            });
        }

        gatilhos.forEach(function (g) {
            g.addEventListener("click", function () {
                mostrar(g.dataset.tab);
            });
        });
        mostrar(root.getAttribute("data-active-tab") || "analise");
    }

    document.addEventListener("click", function (evento) {
        var botao = evento.target.closest("[data-limpar]");
        if (botao) {
            limparFormulario(botao);
        }
    });

    // Copiar resultado da Análise: lê a textarea oculta que vem dentro do fragmento htmx trocado.
    function bootCopiar() {
        var btn = document.getElementById("btn-copiar-resultado");
        if (!btn) {
            return;
        }
        btn.addEventListener("click", function () {
            var el = document.getElementById("texto-copia-oculto");
            var texto = el ? el.value : "";
            if (!texto) {
                btn.textContent = "Sem resultado";
                setTimeout(function () { btn.innerHTML = '<span class="material-symbols-outlined">content_copy</span> Copiar resultado'; }, 1500);
                return;
            }
            navigator.clipboard.writeText(texto).then(
                function () { btn.textContent = "✅ Copiado"; },
                function () { btn.textContent = "❌ Falhou"; }
            );
            setTimeout(function () { btn.innerHTML = '<span class="material-symbols-outlined">content_copy</span> Copiar resultado'; }, 1500);
        });
    }

    // Mantém os links de Exportar JSON/PDF apontando para o endereço analisado no momento.
    function sincronizarExport() {
        var campo = document.getElementById("ipv6Endereco");
        var linkJson = document.getElementById("ipv6-export-json");
        var linkPdf = document.getElementById("ipv6-export-pdf");
        if (!campo || (!linkJson && !linkPdf)) {
            return;
        }
        var valor = encodeURIComponent(campo.value || "");
        if (linkJson) {
            linkJson.setAttribute("href", "/ipv6/export/json?endereco=" + valor);
        }
        if (linkPdf) {
            linkPdf.setAttribute("href", "/ipv6/export/pdf?endereco=" + valor);
        }
    }

    document.addEventListener("htmx:afterSwap", function (e) {
        if (e.target && e.target.id === "saidaCalc") {
            sincronizarExport();
        }
    });

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            bootAbas();
            bootCopiar();
            sincronizarExport();
        });
    } else {
        bootAbas();
        bootCopiar();
        sincronizarExport();
    }
})();

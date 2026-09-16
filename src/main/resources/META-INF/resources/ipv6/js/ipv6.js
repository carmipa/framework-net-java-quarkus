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

    // Localidades dinâmicas do Projetar: adicionar/remover linhas (como no IPv4).
    function novaLinhaLocal() {
        var row = document.createElement("div");
        row.className = "row g-2 align-items-end proj-local-row mb-2";
        row.innerHTML =
            '<div class="col-lg-10 col-9"><input type="text" name="local" class="aed-input form-control" ' +
            'placeholder="Nome da localidade / VLAN" autocomplete="off" required></div>' +
            '<div class="col-lg-2 col-3 d-grid"><button type="button" class="aed-btn aed-btn-danger btn-sm proj-remove-local" ' +
            'title="Remover esta localidade"><span class="material-symbols-outlined">delete</span></button></div>';
        return row;
    }

    document.addEventListener("click", function (evento) {
        var add = evento.target.closest("#proj-add-local");
        if (add) {
            var cont = document.getElementById("proj-locais-container");
            if (cont) {
                var row = novaLinhaLocal();
                cont.appendChild(row);
                var inp = row.querySelector("input");
                if (inp) {
                    inp.focus();
                }
            }
            return;
        }
        var rem = evento.target.closest(".proj-remove-local");
        if (rem) {
            var cont2 = document.getElementById("proj-locais-container");
            var rows = cont2 ? cont2.querySelectorAll(".proj-local-row") : [];
            if (rows.length > 1) {
                rem.closest(".proj-local-row").remove();
            } else {
                // mantém ao menos uma linha; só limpa o campo
                var only = rem.closest(".proj-local-row").querySelector("input");
                if (only) {
                    only.value = "";
                }
            }
        }
    });

    // Baixar / copiar o plano do Projetar (texto vem no fragmento htmx).
    document.addEventListener("click", function (evento) {
        var baixar = evento.target.closest("[data-baixar-plano]");
        if (baixar) {
            var el = document.getElementById("ipv6-plano-texto");
            var texto = el ? el.value : "";
            if (!texto) {
                return;
            }
            var blob = new Blob([texto], { type: "text/plain;charset=utf-8" });
            var url = URL.createObjectURL(blob);
            var a = document.createElement("a");
            a.href = url;
            a.download = "plano-ipv6.txt";
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
            return;
        }
        var copiar = evento.target.closest("[data-copiar-plano]");
        if (copiar) {
            var el2 = document.getElementById("ipv6-plano-texto");
            var t2 = el2 ? el2.value : "";
            if (t2) {
                navigator.clipboard.writeText(t2).then(
                    function () { copiar.textContent = "✅ Copiado"; },
                    function () { copiar.textContent = "❌ Falhou"; }
                );
                setTimeout(function () { copiar.innerHTML = '<span class="material-symbols-outlined">content_copy</span> Copiar plano'; }, 1500);
            }
        }
    });

    // Diagrama de arquitetura: renderiza o Mermaid que veio no fragmento htmx.
    function renderizarMermaid(escopo) {
        if (!window.mermaid) {
            return;
        }
        var alvos = (escopo || document).querySelectorAll(".mermaid:not([data-processed])");
        if (!alvos.length) {
            return;
        }
        try {
            window.mermaid.run({ nodes: alvos });
        } catch (e) {
            /* diagrama é auxiliar; não quebra a página */
        }
    }

    document.addEventListener("htmx:afterSwap", function (e) {
        if (e.target && (e.target.id === "saidaProjetar")) {
            renderizarMermaid(e.target);
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

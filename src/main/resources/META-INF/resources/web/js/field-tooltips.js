(function (w) {
    "use strict";

    const SKIP_TYPES = new Set([
        "hidden", "submit", "button", "reset", "checkbox", "radio", "range", "file", "image", "color"
    ]);

    function cleanText(text) {
        return (text || "").replace(/\s+/g, " ").trim();
    }

    /**
     * Texto que uma pessoa lê no rótulo, sem o nome do glifo e sem os controles de dentro dele.
     * Auditoria FRONT-02/12: textContent trazia "lan Bloco base" (o nome do ícone Material Symbols) e,
     * com o select dentro do label, "Linhas por página 8 10 25...".
     */
    function textoVisivel(no) {
        const copia = no.cloneNode(true);
        copia.querySelectorAll(
            ".material-symbols-outlined, [aria-hidden='true'], select, option, input, textarea, button, .visually-hidden-focusable"
        ).forEach((n) => n.remove());
        return cleanText(copia.textContent);
    }

    /**
     * Propósito: a dica automática de um campo sem title diz o que o campo é.
     * Invariante: só usa rótulo DO PRÓPRIO campo; o rótulo de outro campo da mesma coluna nunca vira a
     * dica (FRONT-02: o VLAN ID recebia "Bloco base"). O aria-label vem antes da busca por vizinhança.
     * Falha: sem rótulo próprio devolve o placeholder ou vazio — sem dica, nunca dica errada.
     */
    function labelTextFor(el) {
        if (el.labels && el.labels.length) {
            const t = textoVisivel(el.labels[0]);
            if (t) {
                return t;
            }
        }
        const aria = cleanText(el.getAttribute("aria-label"));
        if (aria) {
            return aria;
        }
        const container = el.closest(".col, [class*='col-'], .mb-3, .form-group, .field") || el.parentElement;
        if (container) {
            const lbl = container.querySelector("label.form-label, label");
            const camposNoContainer = container.querySelectorAll("input, select, textarea").length;
            if (lbl && !lbl.htmlFor && camposNoContainer === 1) {
                const t = textoVisivel(lbl);
                if (t) {
                    return t;
                }
            }
        }
        return cleanText(el.getAttribute("placeholder"));
    }

    function ensureTooltip(el) {
        if (el.tagName === "INPUT") {
            const type = (el.getAttribute("type") || "text").toLowerCase();
            if (SKIP_TYPES.has(type)) {
                return;
            }
        }
        if (w.bootstrap && w.bootstrap.Tooltip && w.bootstrap.Tooltip.getInstance(el)) {
            return; // já tem tooltip: recriar o title faria a dica nativa aparecer junto (FRONT-12)
        }
        let title = el.getAttribute("title") || el.getAttribute("data-bs-title")
            || el.getAttribute("data-bs-original-title");
        if (!title || !title.trim()) {
            const base = labelTextFor(el);
            if (base) {
                title = base;
                el.setAttribute("title", title);
            }
        }
        if (title && title.trim()) {
            el.setAttribute("data-bs-toggle", "tooltip");
            if (!el.getAttribute("data-bs-placement")) {
                el.setAttribute("data-bs-placement", "top");
            }
        }
    }

    /**
     * Propósito: o Bootstrap troca o aria-describedby do elemento pelo id do tooltip ao mostrar e o
     * APAGA ao esconder; a dica ou o erro que o campo já descrevia some para o leitor de tela depois do
     * primeiro foco.
     * Invariante: a descrição original do elemento continua lá antes, durante e depois do tooltip.
     * Falha: sem descrição original não faz nada; evento que não chega deixa o comportamento do Bootstrap.
     */
    function preservarDescricao(el) {
        if (el.hasAttribute("data-descr-original")) {
            return;
        }
        const original = (el.getAttribute("aria-describedby") || "").trim();
        el.setAttribute("data-descr-original", original);
        if (!original) {
            return;
        }
        const juntar = () => {
            const ids = original.split(/\s+/).concat((el.getAttribute("aria-describedby") || "").split(/\s+/));
            el.setAttribute("aria-describedby", Array.from(new Set(ids.filter(Boolean))).join(" "));
        };
        el.addEventListener("inserted.bs.tooltip", juntar);
        el.addEventListener("shown.bs.tooltip", juntar);
        el.addEventListener("hidden.bs.tooltip", () => el.setAttribute("aria-describedby", original));
    }

    function init(root) {
        const scope = root || document;
        scope.querySelectorAll("input, select, textarea").forEach(ensureTooltip);
        
        scope.querySelectorAll("[title]:not([data-bs-toggle])").forEach((el) => {
            const t = el.getAttribute("title");
            if (t && t.trim()) {
                el.setAttribute("data-bs-toggle", "tooltip");
                if (!el.getAttribute("data-bs-placement")) {
                    el.setAttribute("data-bs-placement", "top");
                }
            }
        });

        if (w.bootstrap && w.bootstrap.Tooltip) {
            scope.querySelectorAll('[data-bs-toggle="tooltip"]').forEach((el) => {
                if (!w.bootstrap.Tooltip.getInstance(el)) {
                    preservarDescricao(el);
                    const tinhaAria = el.hasAttribute("aria-label");
                    new w.bootstrap.Tooltip(el);
                    // O Bootstrap põe a dica como aria-label em elemento sem texto (todo campo): o nome
                    // acessível do campo deixava de ser o rótulo e virava a dica (FRONT-02). Campo com
                    // rótulo próprio fica com o rótulo; a dica segue como descrição.
                    if (!tinhaAria && el.labels && el.labels.length) {
                        el.removeAttribute("aria-label");
                    }
                }
            });
        }

        if (w.bootstrap && w.bootstrap.Popover) {
            scope.querySelectorAll('[data-bs-toggle="popover"]').forEach((el) => {
                if (!w.bootstrap.Popover.getInstance(el)) {
                    new w.bootstrap.Popover(el);
                }
            });
        }
    }

    /**
     * Esc fecha qualquer dica ou popover aberto (WCAG 1.4.13, auditoria FRONT-10): quem navega por
     * teclado não tinha como tirar a dica de cima do conteúdo.
     */
    document.addEventListener("keydown", (ev) => {
        if (ev.key !== "Escape" || !w.bootstrap) {
            return;
        }
        document.querySelectorAll('[data-bs-toggle="tooltip"], [data-bs-toggle="popover"]').forEach((el) => {
            const dica = (w.bootstrap.Tooltip && w.bootstrap.Tooltip.getInstance(el))
                || (w.bootstrap.Popover && w.bootstrap.Popover.getInstance(el));
            if (dica) {
                dica.hide();
            }
        });
    });

    document.addEventListener("DOMContentLoaded", () => init(document));
    w.FieldTooltips = { init };
})(window);

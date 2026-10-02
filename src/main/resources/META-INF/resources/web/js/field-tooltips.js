(function (w) {
    "use strict";

    const SKIP_TYPES = new Set([
        "hidden", "submit", "button", "reset", "checkbox", "radio", "range", "file", "image", "color"
    ]);

    function cleanText(text) {
        return (text || "").replace(/\s+/g, " ").trim();
    }

    function labelTextFor(el) {
        // 1. <label for="id">
        if (el.id) {
            try {
                const lbl = document.querySelector('label[for="' + (w.CSS && CSS.escape ? CSS.escape(el.id) : el.id) + '"]');
                if (lbl) {
                    return cleanText(lbl.textContent);
                }
            } catch (_) {
                /* seletor inválido: ignora */
            }
        }
        // 2. campo dentro de um <label>
        const wrapLabel = el.closest("label");
        if (wrapLabel) {
            return cleanText(wrapLabel.textContent);
        }
        // 3. <label> no mesmo container (col/campo)
        const container = el.closest(".col, [class*='col-'], .mb-3, .form-group, .field") || el.parentElement;
        if (container) {
            const lbl = container.querySelector("label.form-label, label");
            if (lbl) {
                return cleanText(lbl.textContent);
            }
        }
        // 4. aria-label / placeholder
        return cleanText(el.getAttribute("aria-label") || el.getAttribute("placeholder"));
    }

    function ensureTooltip(el) {
        if (el.tagName === "INPUT") {
            const type = (el.getAttribute("type") || "text").toLowerCase();
            if (SKIP_TYPES.has(type)) {
                return;
            }
        }
        let title = el.getAttribute("title") || el.getAttribute("data-bs-title");
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
                    new w.bootstrap.Tooltip(el);
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

    document.addEventListener("DOMContentLoaded", () => init(document));
    w.FieldTooltips = { init };
})(window);

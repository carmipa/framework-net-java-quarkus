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

    function init(root) {
        const scope = root || document;
        scope.querySelectorAll("input, select, textarea").forEach(ensureTooltip);
        if (w.bootstrap && w.bootstrap.Tooltip) {
            scope.querySelectorAll('[data-bs-toggle="tooltip"]').forEach((el) => {
                if (!w.bootstrap.Tooltip.getInstance(el)) {
                    new w.bootstrap.Tooltip(el);
                }
            });
        }
    }

    document.addEventListener("DOMContentLoaded", () => init(document));
    w.FieldTooltips = { init };
})(window);

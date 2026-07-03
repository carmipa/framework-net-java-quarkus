(function (w) {
    "use strict";

    function parseIPv4(value) {
        const parts = String(value || "").trim().split(".");
        if (parts.length !== 4) return null;
        const nums = parts.map((p) => Number(p));
        if (nums.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return null;
        return nums;
    }

    function toDotted(nums) {
        return nums.join(".");
    }

    function invertOctets(nums) {
        return nums.map((n) => 255 - n);
    }

    function prefixFromMask(nums) {
        return nums.map((n) => n.toString(2).padStart(8, "0")).join("").split("").filter((b) => b === "1").length;
    }

    function toMask(prefix) {
        const bits = (0xffffffff << (32 - prefix)) >>> 0;
        return [24, 16, 8, 0].map((shift) => (bits >>> shift) & 255).join(".");
    }

    function toWildcard(mask) {
        return mask.split(".").map((n) => String(255 - Number(n))).join(".");
    }

    function initMaskHostsSlider() {
        const slider = document.getElementById("mask_hosts_slider");
        if (!slider) return;
        const hostsEl = document.getElementById("mask_hosts_value");
        const neededEl = document.getElementById("mask_needed_value");
        const prefixEl = document.getElementById("mask_prefix_value");
        const maskEl = document.getElementById("mask_decimal_value");
        const wildEl = document.getElementById("mask_wildcard_value");

        const update = () => {
            const hosts = Number(slider.value);
            const needed = hosts + 2;
            const hostBits = Math.ceil(Math.log2(needed));
            const prefix = 32 - hostBits;
            const mask = toMask(prefix);
            if (hostsEl) hostsEl.textContent = String(hosts);
            if (neededEl) neededEl.textContent = String(needed);
            if (prefixEl) prefixEl.textContent = "/" + prefix;
            if (maskEl) maskEl.textContent = mask;
            if (wildEl) wildEl.textContent = toWildcard(mask);
        };
        slider.addEventListener("input", update);
        update();
    }

    function initMaskBinaryViewer() {
        const input = document.getElementById("mask_binary_input");
        const rows = document.getElementById("mask_binary_rows");
        const meta = document.getElementById("mask_binary_meta");
        if (!input || !rows || !meta) return;

        const render = () => {
            const nums = parseIPv4(input.value);
            if (!nums) {
                rows.innerHTML = '<div class="text-danger">Máscara inválida. Use formato x.x.x.x.</div>';
                meta.textContent = "";
                return;
            }
            const bins = nums.map((n) => n.toString(2).padStart(8, "0"));
            const ones = bins.join("").split("").filter((b) => b === "1").length;
            const zeros = 32 - ones;
            meta.innerHTML = "Prefixo estimado: <strong class=\"text-info\">/" + ones
                + "</strong> | Bits de rede: <strong class=\"text-light\">" + ones
                + "</strong> | Bits de host: <strong class=\"text-light\">" + zeros + "</strong>";
            rows.innerHTML = bins.map((bin, idx) => {
                const colored = bin.split("").map((b) => (
                    b === "1"
                        ? '<span class="text-success fw-bold">' + b + "</span>"
                        : '<span class="text-warning">' + b + "</span>"
                )).join("");
                return '<div class="mb-1"><span class="text-secondary me-2">Octeto ' + (idx + 1)
                    + ":</span><code>" + nums[idx] + "</code> = <code>" + colored + "</code></div>";
            }).join("");
        };
        input.addEventListener("input", render);
        render();
    }

    function initMaskReferenceTable() {
        const tbody = document.getElementById("mask-ref-tbody");
        if (!tbody) return;
        fetch("/mascara-referencia")
            .then((r) => r.json())
            .then((data) => {
                const rows = data.table || (Array.isArray(data) ? data : (data.items || data.rows || []));
                if (!rows.length) return;
                tbody.innerHTML = rows.map((row) => {
                    const prefix = row.prefix != null ? row.prefix : row.cidr;
                    return "<tr><td>/" + prefix + "</td><td>" + (row.mask || "") + "</td><td>"
                        + (row.wildcard || "") + "</td><td>" + (row.hosts != null ? row.hosts : "") + "</td></tr>";
                }).join("");
            })
            .catch(() => {
                tbody.innerHTML = '<tr><td colspan="4" class="text-warning small">Não foi possível carregar /mascara-referencia.</td></tr>';
            });
    }

    function initWildcardConverter() {
        const maskInput = document.getElementById("wild_mask_input");
        const wildcardInput = document.getElementById("wild_wildcard_input");
        const meta = document.getElementById("wild_convert_meta");
        const swapBtn = document.getElementById("wild_swap_btn");
        if (!maskInput || !wildcardInput || !meta) return;

        const renderFromMask = () => {
            const m = parseIPv4(maskInput.value);
            if (!m) {
                meta.innerHTML = '<span class="text-danger">Máscara inválida para conversão.</span>';
                return;
            }
            const inv = invertOctets(m);
            wildcardInput.value = toDotted(inv);
            const prefix = prefixFromMask(m);
            meta.innerHTML = "/" + prefix + ' | Máscara <strong class="text-light">' + toDotted(m)
                + '</strong> → Wildcard <strong class="text-warning">' + toDotted(inv) + "</strong>.";
        };

        const renderFromWildcard = () => {
            const wild = parseIPv4(wildcardInput.value);
            if (!wild) {
                meta.innerHTML = '<span class="text-danger">Wildcard inválida para conversão.</span>';
                return;
            }
            const m = invertOctets(wild);
            maskInput.value = toDotted(m);
            const prefix = prefixFromMask(m);
            meta.innerHTML = "/" + prefix + ' | Wildcard <strong class="text-warning">' + toDotted(wild)
                + '</strong> → Máscara <strong class="text-light">' + toDotted(m) + "</strong>.";
        };

        maskInput.addEventListener("input", renderFromMask);
        wildcardInput.addEventListener("input", renderFromWildcard);
        if (swapBtn) {
            swapBtn.addEventListener("click", () => {
                const tmp = maskInput.value;
                maskInput.value = wildcardInput.value;
                wildcardInput.value = tmp;
                renderFromMask();
            });
        }
        renderFromMask();
    }

    function initCopyCliButtons() {
        document.querySelectorAll(".copy-cli-model").forEach((btn) => {
            btn.addEventListener("click", async () => {
                const original = btn.textContent;
                const txt = (btn.getAttribute("data-copy-text") || "").replace(/&#10;/g, "\n");
                try {
                    await navigator.clipboard.writeText(txt);
                    btn.textContent = "✅ Copiado";
                } catch (_) {
                    btn.textContent = "❌ Falhou";
                }
                setTimeout(() => { btn.textContent = original; }, 1200);
            });
        });
    }

    function initWildcardResultLab() {
        const maskInput = document.getElementById("wild_result_mask");
        const wildInput = document.getElementById("wild_result_wild");
        const meta = document.getElementById("wild_result_meta");
        if (!maskInput || !wildInput || !meta) return;

        const sync = () => {
            const m = parseIPv4(maskInput.value);
            if (!m) {
                meta.textContent = "Máscara inválida.";
                return;
            }
            const w = invertOctets(m);
            wildInput.value = toDotted(w);
            const ok = m.every((oct, i) => oct + w[i] === 255);
            meta.innerHTML = ok
                ? '<span class="text-success">Validação: wildcard + máscara = 255 em cada octeto.</span>'
                : '<span class="text-danger">Octetos não somam 255 — revise a conversão.</span>';
        };
        maskInput.addEventListener("input", sync);
        wildInput.addEventListener("input", () => {
            const w = parseIPv4(wildInput.value);
            if (w) maskInput.value = toDotted(invertOctets(w));
            sync();
        });
        sync();
    }

    function bootWidgets() {
        initMaskReferenceTable();
        initMaskHostsSlider();
        initMaskBinaryViewer();
        initWildcardConverter();
        initCopyCliButtons();
        initWildcardResultLab();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bootWidgets);
    } else {
        bootWidgets();
    }

    w.initAnaliseWidgets = bootWidgets;
})(window);

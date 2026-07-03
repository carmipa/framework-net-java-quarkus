(function () {
    const normalize = (value) => (value || "").toString().toLowerCase();
    const parsePortValue = (value) => {
        const raw = (value || "").toString();
        const match = raw.match(/\d+/);
        return match ? Number(match[0]) : Number.MAX_SAFE_INTEGER;
    };
    const riskOrder = { "crítico": 1, "atenção": 2, "seguro": 3, "didático": 4 };

    const registerCatalogHistory = (modo, entrada) => {
        if (!modo || !entrada) return;
        fetch("/history/catalog", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "same-origin",
            body: JSON.stringify({ modo, entrada }),
        }).catch(() => {});
    };

    const updateSummary = (gridId, rows) => {
        const summaryTarget = document.querySelector(`[data-summary-target="${gridId}"]`);
        const statsBar = document.querySelector(`[data-summary-stats="${gridId}"]`);
        const total = rows.length;
        const stats = { "crítico": 0, "atenção": 0, "seguro": 0, "didático": 0 };
        rows.forEach((row) => {
            const txt = normalize(row.dataset.risco || row.dataset.nivel);
            Object.keys(stats).forEach((k) => {
                if (txt.includes(k)) stats[k] += 1;
            });
        });
        if (statsBar) {
            const setStat = (key, val) => {
                const el = statsBar.querySelector(`[data-stat="${key}"]`);
                if (el) el.textContent = String(val);
            };
            setStat("total", total);
            setStat("critico", stats["crítico"]);
            setStat("atencao", stats["atenção"]);
            setStat("seguro", stats["seguro"]);
        }
        if (summaryTarget && !summaryTarget.classList.contains("aed-stats-bar")) {
            summaryTarget.innerHTML = `
            <strong>Total:</strong> ${total}
            <span class="ms-2 text-danger">Críticas: ${stats["crítico"]}</span>
            <span class="ms-2 text-warning">Atenção: ${stats["atenção"]}</span>
            <span class="ms-2 text-success">Seguras: ${stats["seguro"]}</span>
            <span class="ms-2 text-info">Didáticas: ${stats["didático"]}</span>
        `;
        }
    };

    const exportRowsJson = (gridId, rows) => {
        const modo = gridId === "portas" || gridId === "protocolos" ? gridId : null;
        const searchEl = document.querySelector(`[data-grid-search="${gridId}"]`);
        if (modo && searchEl && searchEl.value && searchEl.value.trim()) {
            registerCatalogHistory(modo, searchEl.value.trim());
        }
        const payload = rows.map((row) => ({ ...row.dataset }));
        const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
        const link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.download = `${gridId}_filtrado.json`;
        link.click();
        URL.revokeObjectURL(link.href);
    };

    const exportRowsPdf = (gridId, rows) => {
        const popup = window.open("", "_blank");
        if (!popup) return;
        const lines = rows.map((r) => {
            const id = r.dataset.porta || r.dataset.protocolo || r.dataset.nome || "";
            const name = r.dataset.servico || r.dataset.descricao || "";
            return `${id} | ${name} | ${r.dataset.transporte || r.dataset.camada || ""} | ${r.dataset.risco || r.dataset.nivel || ""}`;
        });
        popup.document.write(`<pre>${gridId.toUpperCase()}\n\n${lines.join("\n")}</pre>`);
        popup.document.close();
        popup.print();
    };

    window.initGrid = function initGrid(gridId) {
        const gridRoot = document.querySelector(`[data-grid-id="${gridId}"]`);
        if (!gridRoot) return;

        const catalogModo = gridId === "portas" || gridId === "protocolos" ? gridId : null;
        let catalogDebounce = null;

        const search = document.querySelector(`[data-grid-search="${gridId}"]`);
        const category = document.querySelector(`[data-grid-category="${gridId}"]`);
        const transport = document.querySelector(`[data-grid-transport="${gridId}"]`);
        const risk = document.querySelector(`[data-grid-risk="${gridId}"]`);
        const clearBtn = document.querySelector(`[data-grid-clear="${gridId}"]`);
        const counter = document.querySelector(`[data-grid-counter="${gridId}"]`);
        const pageLabel = document.querySelector(`[data-grid-page-label="${gridId}"]`);
        const prevBtn = document.querySelector(`[data-grid-prev="${gridId}"]`);
        const nextBtn = document.querySelector(`[data-grid-next="${gridId}"]`);
        const exportJsonBtn = document.querySelector(`[data-grid-export-json="${gridId}"]`);
        const exportPdfBtn = document.querySelector(`[data-grid-export-pdf="${gridId}"]`);
        const table = document.querySelector(`[data-grid-table="${gridId}"]`);
        const tbody = document.querySelector(`tbody[data-grid-body="${gridId}"]`);
        const pageSizeSelect = document.querySelector(`[data-grid-page-size="${gridId}"]`);
        const detailsModalEl = document.getElementById("commDetailsModal");
        const detailsModal = detailsModalEl ? new bootstrap.Modal(detailsModalEl) : null;
        const detailsBody = document.getElementById("commDetailsModalBody");
        const detailsTitle = document.getElementById("commDetailsModalLabel");

        const isMobileGrid = () => window.matchMedia("(max-width: 62em)").matches;

        const getRows = () => {
            if (isMobileGrid()) {
                return [...document.querySelectorAll(`[data-grid-mobile="${gridId}"] [data-grid-mobile-row="${gridId}"]`)];
            }
            return [...document.querySelectorAll(`tbody[data-grid-body="${gridId}"] tr[data-grid-row="${gridId}"]`)];
        };

        const rowContainer = () => {
            if (isMobileGrid()) {
                return document.querySelector(`[data-grid-mobile="${gridId}"]`) || null;
            }
            return tbody;
        };

        let sortKey = "";
        let sortDir = 1;
        let page = 1;

        const cacheApi = window.SearchCache;
        const cached = cacheApi && typeof cacheApi.loadGridState === "function"
            ? cacheApi.loadGridState(gridId)
            : null;
        if (cached) {
            if (search && cached.search != null) search.value = cached.search;
            if (category && cached.category) category.value = cached.category;
            if (transport && cached.transport) transport.value = cached.transport;
            if (risk && cached.risk) risk.value = cached.risk;
            if (pageSizeSelect && cached.pageSize) pageSizeSelect.value = cached.pageSize;
            if (cached.sortKey) sortKey = cached.sortKey;
            if (cached.sortDir) sortDir = cached.sortDir;
            if (cached.page) page = cached.page;
        }

        const persistGridState = () => {
            if (!cacheApi || typeof cacheApi.saveGridState !== "function") {
                return;
            }
            cacheApi.saveGridState(gridId, {
                search: search ? search.value : "",
                category: category ? category.value : "all",
                transport: transport ? transport.value : "all",
                risk: risk ? risk.value : "all",
                pageSize: pageSizeSelect ? pageSizeSelect.value : "8",
                sortKey,
                sortDir,
                page,
            });
        };

        const getPageLimit = () => {
            if (!pageSizeSelect) return 8;
            const v = pageSizeSelect.value;
            if (v === "all") return null;
            const n = parseInt(v, 10);
            return Number.isFinite(n) && n > 0 ? n : 10;
        };

        const getFiltered = () => {
            const rowEls = getRows();
            return rowEls.filter((row) => {
                const text = normalize(row.dataset.search);
                const term = normalize(search && search.value);
                const cat = normalize(category && category.value);
                const trans = normalize(transport && transport.value);
                const r = normalize(risk && risk.value);
                const categoryValue = normalize(row.dataset.categoria);
                const transportValue = normalize(row.dataset.transporte || "");
                const riskValue = normalize(row.dataset.risco || row.dataset.nivel || "");
                const termOk = !term || text.includes(term);
                const catOk = cat === "all" || categoryValue === cat;
                const transOk = trans === "all" || transportValue.includes(trans);
                const riskOk = r === "all" || riskValue.includes(r);
                return termOk && catOk && transOk && riskOk;
            });
        };

        const apply = () => {
            const rows = getRows();
            if (!rows.length) return;
            let filtered = getFiltered();
            if (sortKey) {
                filtered = [...filtered].sort((a, b) => {
                    const va = normalize(a.dataset[sortKey]);
                    const vb = normalize(b.dataset[sortKey]);
                    if (sortKey === "porta") return (parsePortValue(va) - parsePortValue(vb)) * sortDir;
                    if (sortKey === "risco") {
                        const rank = (v) => riskOrder[v.includes("crítico") ? "crítico" : v.includes("atenção") ? "atenção" : v.includes("seguro") ? "seguro" : "didático"] || 9;
                        return (rank(va) - rank(vb)) * sortDir;
                    }
                    return va.localeCompare(vb, "pt-BR") * sortDir;
                });
            }
            rows.forEach((r) => r.classList.add("d-none"));
            const limit = getPageLimit();
            const len = filtered.length;
            const totalPages = limit === null ? 1 : Math.max(1, Math.ceil(len / limit));
            if (page > totalPages) page = totalPages;
            const start = limit === null ? 0 : (page - 1) * limit;
            const sliceEnd = limit === null ? len : Math.min(start + limit, len);
            const current = filtered.slice(start, sliceEnd);
            const container = rowContainer();
            if (container) container.append(...current);
            current.forEach((r) => r.classList.remove("d-none"));
            if (counter) counter.textContent = `${filtered.length} registro(s)`;
            if (pageLabel) {
                if (limit === null) {
                    pageLabel.textContent = len ? `Exibindo todos (${len})` : "—";
                } else {
                    pageLabel.textContent = `Página ${page}/${totalPages}`;
                }
            }
            const paginate = limit !== null;
            if (prevBtn) prevBtn.disabled = !paginate || page <= 1;
            if (nextBtn) nextBtn.disabled = !paginate || page >= totalPages;
            updateSummary(gridId, filtered);
            if (catalogModo && search && search.value && search.value.trim()) {
                clearTimeout(catalogDebounce);
                catalogDebounce = setTimeout(() => {
                    registerCatalogHistory(catalogModo, search.value.trim());
                }, 800);
            }
            persistGridState();
            return filtered;
        };

        table && table.querySelectorAll(".sortable").forEach((th) => th.addEventListener("click", () => {
            const key = th.dataset.sortKey;
            if (sortKey === key) sortDir *= -1;
            else {
                sortKey = key;
                sortDir = 1;
            }
            apply();
        }));

        [search, category, transport, risk].forEach((el) => el && el.addEventListener("input", () => {
            page = 1;
            apply();
        }));
        [category, transport, risk].forEach((el) => el && el.addEventListener("change", () => {
            page = 1;
            apply();
        }));
        pageSizeSelect && pageSizeSelect.addEventListener("change", () => {
            page = 1;
            apply();
        });

        prevBtn && prevBtn.addEventListener("click", () => {
            if (page > 1) {
                page -= 1;
                apply();
            }
        });
        nextBtn && nextBtn.addEventListener("click", () => {
            const limit = getPageLimit();
            if (limit !== null) {
                const totalPages = Math.max(1, Math.ceil(getFiltered().length / limit));
                if (page >= totalPages) return;
            }
            page += 1;
            apply();
        });
        clearBtn && clearBtn.addEventListener("click", () => {
            if (search) search.value = "";
            if (category) category.value = "all";
            if (transport) transport.value = "all";
            if (risk) risk.value = "all";
            if (pageSizeSelect) pageSizeSelect.value = "8";
            sortKey = "";
            sortDir = 1;
            page = 1;
            if (cacheApi && typeof cacheApi.clearGridState === "function") {
                cacheApi.clearGridState(gridId);
            }
            apply();
        });
        exportJsonBtn && exportJsonBtn.addEventListener("click", () => exportRowsJson(gridId, apply()));
        exportPdfBtn && exportPdfBtn.addEventListener("click", () => exportRowsPdf(gridId, apply()));

        gridRoot.addEventListener("click", async (ev) => {
            const copyBtn = ev.target.closest(`[data-grid-copy="${gridId}"]`);
            const detailsBtn = ev.target.closest(`[data-grid-details="${gridId}"]`);
            const row = ev.target.closest(`[data-grid-row="${gridId}"], [data-grid-mobile-row="${gridId}"]`);
            if (!row) return;
            if (copyBtn) {
                await navigator.clipboard.writeText(JSON.stringify({ ...row.dataset }, null, 2));
            }
            if (detailsBtn && detailsModal) {
                const label = row.dataset.porta || row.dataset.protocolo || "";
                const title = row.dataset.servico || row.dataset.nome || "";
                detailsTitle.textContent = `${label} - ${title}`;
                detailsBody.innerHTML = `
                    <p><strong>Categoria:</strong> ${row.dataset.categoria || row.dataset.camada || ""}</p>
                    <p><strong>Transporte:</strong> ${row.dataset.transporte || "—"}</p>
                    <p><strong>Risco:</strong> ${row.dataset.risco || row.dataset.nivel || ""}</p>
                    <p><strong>Recomendação:</strong> ${row.dataset.recomendacao || ""}</p>
                    <p><strong>Alternativa segura:</strong> ${row.dataset.alternativa || ""}</p>
                `;
                detailsModal.show();
            }
        });

        let gridResizeTimer;
        window.addEventListener("resize", () => {
            clearTimeout(gridResizeTimer);
            gridResizeTimer = setTimeout(() => {
                page = 1;
                apply();
            }, 200);
        });

        apply();
    };
})();

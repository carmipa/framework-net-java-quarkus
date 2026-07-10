(function () {
    const ITENS_POR_PAGINA = 15;
    let historicoCompleto = [];
    let consoleLinhas = [];
    let termoBusca = '';
    let paginaAtual = 1;
    let janelaMinutos = 60;
    let autoRefreshMs = 10000;
    let consolePausado = false;
    let timerAutoRefresh = null;
    let chartModulos, chartStatus, chartAtividade, chartLatencia;

    const CORES_MODULO = ['#f59e0b', '#60a5fa', '#34d399', '#818cf8', '#f43f5e', '#2dd4bf', '#c084fc', '#fb923c'];
    const CORES_STATUS = { '2xx': '#34d399', '3xx': '#60a5fa', '4xx': '#f59e0b', '5xx': '#f43f5e' };
    const PERIODO_LABEL = {
        '15': 'últimos 15 min', '60': 'última hora', '360': 'últimas 6 h',
        '1440': 'últimas 24 h', '10080': 'últimos 7 dias', '0': 'todo o histórico'
    };

    const esc = (s) => (window.HtmlEscape && window.HtmlEscape.esc) ? window.HtmlEscape.esc(s) : String(s == null ? '' : s);

    function $(id) { return document.getElementById(id); }

    function configurarTemaChart() {
        if (typeof Chart === 'undefined') return;
        Chart.defaults.color = '#8b97ad';
        Chart.defaults.font.family = "'Inter Tight', system-ui, sans-serif";
        Chart.defaults.font.size = 11;
        Chart.defaults.plugins.legend.labels.boxWidth = 8;
        Chart.defaults.plugins.legend.labels.usePointStyle = true;
        Chart.defaults.plugins.tooltip.backgroundColor = '#0c1422';
        Chart.defaults.plugins.tooltip.usePointStyle = true;
        Chart.defaults.scale.grid.color = 'rgba(255,255,255,0.05)';
    }

    function formatarHorario(iso) {
        if (!iso) return '--';
        try { return new Date(iso).toLocaleString('pt-BR'); } catch (_) { return iso; }
    }

    function ms(v) { return (v == null ? 0 : v) + ' ms'; }

    // ---------- KPIs ----------
    function atualizarMetricas(data) {
        const lat = data.latencia || {};
        const resumo = data.resumo || {};
        $('t-http-total').textContent = String(data.httpTotal || 0);
        $('t-taxa-sucesso').textContent = (data.httpTotal ? (data.taxaSucesso || 0) : 0) + '%';
        $('t-http-4xx').textContent = String(data.http4xx || 0);
        $('t-http-5xx').textContent = String(data.http5xx || 0);
        $('t-p95').textContent = ms(lat.p95);
        $('t-latencia-sub').textContent = 'P50 ' + ms(lat.p50) + ' · P99 ' + ms(lat.p99);
        $('t-total-eventos').textContent = String(resumo.totalEventos || 0);
        const nMods = (data.porModulo || []).length;
        $('t-modulos-sub').textContent = nMods + (nMods === 1 ? ' módulo' : ' módulos');

        const okTaxa = $('t-taxa-sucesso');
        okTaxa.classList.toggle('kpi-bad', data.httpTotal > 0 && (data.taxaSucesso || 0) < 90);
        $('t-http-5xx').classList.toggle('kpi-bad', (data.http5xx || 0) > 0);

        if ($('t-atualizado')) $('t-atualizado').textContent = data.atualizadoEm || '--';
        if ($('t-periodo-label')) $('t-periodo-label').textContent = 'janela: ' + (PERIODO_LABEL[String(data.janelaMinutos)] || (data.janelaMinutos + ' min'));
        const pasta = $('t-pasta-logs');
        if (pasta) pasta.textContent = data.pastaLogs ? 'Logs: ' + data.pastaLogs : '';
    }

    // ---------- Gráficos ----------
    function atualizarGraficos(data) {
        const modulos = data.porModulo || [];
        const labelsMod = modulos.map(m => m.modulo);
        const valoresMod = modulos.map(m => m.total);
        if (chartModulos) chartModulos.destroy();
        chartModulos = new Chart($('chart-modulos'), {
            type: 'bar',
            data: {
                labels: labelsMod,
                datasets: [{
                    label: 'Requisições', data: valoresMod,
                    backgroundColor: labelsMod.map((_, i) => CORES_MODULO[i % CORES_MODULO.length]),
                    borderRadius: 6, barPercentage: 0.7
                }]
            },
            options: {
                indexAxis: 'y', responsive: true,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { afterBody: (items) => {
                        const m = modulos[items[0].dataIndex];
                        return ['OK: ' + m.ok + '  Erros: ' + m.erro, 'P95: ' + ms(m.p95)];
                    } } }
                },
                scales: { x: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        const stKeys = ['2xx', '3xx', '4xx', '5xx'];
        const stVals = [data.http2xx || 0, data.http3xx || 0, data.http4xx || 0, data.http5xx || 0];
        if (chartStatus) chartStatus.destroy();
        chartStatus = new Chart($('chart-status'), {
            type: 'doughnut',
            data: {
                labels: stKeys,
                datasets: [{ data: stVals, backgroundColor: stKeys.map(k => CORES_STATUS[k]), borderColor: '#0c121c', borderWidth: 3 }]
            },
            options: {
                responsive: true, cutout: '68%',
                plugins: { legend: { position: 'bottom' } }
            },
            plugins: [{
                id: 'centerTotal',
                beforeDraw(chart) {
                    const { ctx, chartArea } = chart;
                    if (!chartArea) return;
                    const total = stVals.reduce((a, b) => a + b, 0);
                    ctx.save();
                    ctx.font = '600 1.1rem "Space Grotesk", sans-serif';
                    ctx.fillStyle = '#eef2f8';
                    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
                    ctx.fillText(String(total), (chartArea.left + chartArea.right) / 2, (chartArea.top + chartArea.bottom) / 2);
                    ctx.restore();
                }
            }]
        });

        const atividade = data.atividadePorMinuto || [];
        const labelsAtv = atividade.map(a => a.minuto);
        if (chartAtividade) chartAtividade.destroy();
        chartAtividade = new Chart($('chart-atividade'), {
            type: 'line',
            data: {
                labels: labelsAtv,
                datasets: [
                    { label: 'Requisições', data: atividade.map(a => a.total), borderColor: '#60a5fa', backgroundColor: 'rgba(96,165,250,0.12)', fill: true, tension: 0.35, pointRadius: 0 },
                    { label: 'Erros', data: atividade.map(a => a.erros), borderColor: '#f43f5e', backgroundColor: 'rgba(244,63,94,0.10)', fill: true, tension: 0.35, pointRadius: 0 }
                ]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: true, position: 'bottom' } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        const lat = data.latencia || {};
        if (chartLatencia) chartLatencia.destroy();
        chartLatencia = new Chart($('chart-latencia'), {
            type: 'bar',
            data: {
                labels: ['P50', 'P90', 'P95', 'P99', 'Máx'],
                datasets: [{
                    label: 'ms', data: [lat.p50 || 0, lat.p90 || 0, lat.p95 || 0, lat.p99 || 0, lat.max || 0],
                    backgroundColor: ['#34d399', '#60a5fa', '#818cf8', '#f59e0b', '#f43f5e'],
                    borderRadius: 6, barPercentage: 0.6
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { callback: (v) => v + ' ms' } } }
            }
        });
    }

    // ---------- Tabelas de endpoints ----------
    function metodoBadge(m) {
        const mm = (m || 'GET').toUpperCase();
        return '<span class="tele-metodo m-' + esc(mm.toLowerCase()) + '">' + esc(mm) + '</span>';
    }

    function renderTopLentos(lista) {
        const tb = $('t-top-lentos');
        if (!tb) return;
        if (!lista || lista.length === 0) {
            tb.innerHTML = '<tr><td colspan="5" class="tele-empty">Sem dados no período.</td></tr>';
            return;
        }
        tb.innerHTML = lista.map(e => `
            <tr>
                <td>${metodoBadge(e.metodo)}</td>
                <td class="col-endpoint" title="${esc(e.endpoint)}">${esc(e.endpoint)}</td>
                <td class="num">${e.chamadas}</td>
                <td class="num mono">${ms(e.p95)}</td>
                <td class="num ${e.erros > 0 ? 'txt-err' : ''}">${e.erros}</td>
            </tr>`).join('');
    }

    function renderTopErros(lista) {
        const tb = $('t-top-erros');
        if (!tb) return;
        if (!lista || lista.length === 0) {
            tb.innerHTML = '<tr><td colspan="5" class="tele-empty">Nenhum erro no período.</td></tr>';
            return;
        }
        tb.innerHTML = lista.map(e => `
            <tr>
                <td>${metodoBadge(e.metodo)}</td>
                <td class="col-endpoint" title="${esc(e.endpoint)}">${esc(e.endpoint)}</td>
                <td class="num ${e.err4xx > 0 ? 'txt-warn' : ''}">${e.err4xx}</td>
                <td class="num ${e.err5xx > 0 ? 'txt-err' : ''}">${e.err5xx}</td>
                <td class="num">${e.chamadas}</td>
            </tr>`).join('');
    }

    // ---------- Console ----------
    function filtrarConsole() {
        const nivel = ($('t-console-nivel') && $('t-console-nivel').value) || '';
        const busca = (($('t-console-busca') && $('t-console-busca').value) || '').toLowerCase();
        return consoleLinhas.filter(l => {
            const s = String(l);
            if (nivel && s.indexOf('[' + nivel + ']') === -1) return false;
            if (busca && s.toLowerCase().indexOf(busca) === -1) return false;
            return true;
        });
    }

    function renderConsole() {
        const el = $('console-telemetria');
        if (!el) return;
        const linhas = filtrarConsole();
        el.textContent = '';
        if (!linhas.length) {
            el.textContent = consoleLinhas.length ? 'Nenhuma linha corresponde ao filtro.' : 'Nenhuma saída registrada ainda.';
            return;
        }
        const frag = document.createDocumentFragment();
        const re = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+\[(\w+)\]\s+([\s\S]*)$/;
        linhas.forEach((linha) => {
            const div = document.createElement('div');
            const m = re.exec(String(linha));
            if (m) {
                const nivel = m[2].toLowerCase();
                const ts = document.createElement('span'); ts.className = 'cl-ts'; ts.textContent = m[1] + ' ';
                const lvl = document.createElement('span');
                lvl.className = 'cl-lvl ' + (nivel === 'warn' || nivel === 'warning' ? 'warn' : nivel === 'error' ? 'error' : 'info');
                lvl.textContent = '[' + m[2] + '] ';
                const msg = document.createElement('span'); msg.className = 'cl-msg'; msg.textContent = m[3];
                div.appendChild(ts); div.appendChild(lvl); div.appendChild(msg);
            } else {
                div.className = 'cl-msg'; div.textContent = String(linha);
            }
            frag.appendChild(div);
        });
        el.appendChild(frag);
        if (!consolePausado) el.scrollTop = el.scrollHeight;
    }

    // ---------- Histórico ----------
    function filtrarHistorico() {
        if (!termoBusca) return historicoCompleto;
        const t = termoBusca.toLowerCase();
        return historicoCompleto.filter(e =>
            [e.evento, e.modulo, e.status, e.message, e.level, e.httpPath, e.httpMethod].join(' ').toLowerCase().includes(t));
    }

    function badgeNivel(level) {
        const l = (level || 'INFO').toUpperCase();
        if (l === 'ERROR') return 'error';
        if (l === 'WARN' || l === 'WARNING') return 'warn';
        return 'info';
    }

    function renderizarTabela() {
        const filtrados = filtrarHistorico();
        const totalPaginas = Math.max(1, Math.ceil(filtrados.length / ITENS_POR_PAGINA));
        if (paginaAtual > totalPaginas) paginaAtual = totalPaginas;
        const inicio = (paginaAtual - 1) * ITENS_POR_PAGINA;
        const pagina = filtrados.slice(inicio, inicio + ITENS_POR_PAGINA);
        const tbody = $('t-table-body');
        const count = $('t-table-count');
        const info = $('t-page-info');

        if (count) count.textContent = filtrados.length === 0 ? '0 evento(s)'
            : filtrados.length + ' evento(s) · exibindo ' + (inicio + 1) + '-' + (inicio + pagina.length);
        if (info) info.textContent = 'Página ' + paginaAtual + ' de ' + totalPaginas;
        if (!tbody) return;
        if (pagina.length === 0) {
            const msg = historicoCompleto.length === 0 ? 'Nenhum evento registrado.' : 'Nenhum evento encontrado.';
            tbody.innerHTML = '<tr><td colspan="8" class="tele-empty">' + msg + '</td></tr>';
            return;
        }
        tbody.innerHTML = pagina.map(e => {
            const st = e.httpStatus != null ? e.httpStatus : (e.status || 'ok');
            const ok = e.httpStatus != null ? (e.httpStatus < 400) : ((e.status || 'ok').toLowerCase() === 'ok');
            return `
            <tr>
                <td class="col-hora">${esc(formatarHorario(e.timestamp))}</td>
                <td><span class="tele-badge ${badgeNivel(e.level)}">${esc((e.level || 'INFO').toUpperCase())}</span></td>
                <td>${esc(e.modulo || '--')}</td>
                <td>${e.httpMethod ? metodoBadge(e.httpMethod) : '--'}</td>
                <td class="col-endpoint" title="${esc(e.httpPath || e.evento || '')}">${esc(e.httpPath || e.evento || '--')}</td>
                <td><span class="tele-status"><span class="tele-dot ${ok ? '' : 'err'}"></span>${esc(st)}</span></td>
                <td class="num mono">${e.durationMs != null ? ms(e.durationMs) : '--'}</td>
                <td class="col-msg" title="${esc(e.message || '')}">${esc(e.message || '')}</td>
            </tr>`;
        }).join('');
    }

    // ---------- Carga ----------
    async function carregarDashboard() {
        try {
            const resp = await fetch('/telemetria/api/dashboard?janela=' + janelaMinutos + '&console=250');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            atualizarMetricas(data);
            atualizarGraficos(data);
            renderTopLentos(data.topLentos);
            renderTopErros(data.topErros);
            if (!consolePausado) {
                consoleLinhas = data.consoleLinhas || [];
                renderConsole();
            }
            historicoCompleto = (data.resumo && data.resumo.ultimosEventos) ? data.resumo.ultimosEventos : [];
            renderizarTabela();
        } catch (err) {
            const el = $('console-telemetria');
            if (el) el.textContent = 'Erro ao carregar telemetria: ' + err.message;
        }
    }

    function reagendarAutoRefresh() {
        if (timerAutoRefresh) { clearInterval(timerAutoRefresh); timerAutoRefresh = null; }
        if (autoRefreshMs > 0) timerAutoRefresh = setInterval(carregarDashboard, autoRefreshMs);
    }

    function configurarConsoleAcoes() {
        document.querySelectorAll('.btn-copy-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                const alvo = $(btn.dataset.target);
                if (alvo) { try { await navigator.clipboard.writeText(alvo.textContent || ''); } catch (_) { /* ignore */ } }
            });
        });
        document.querySelectorAll('.btn-clear-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                try { await fetch('/telemetria/api/console/limpar', { method: 'POST' }); } catch (_) { /* ignore */ }
                consoleLinhas = [];
                const alvo = $(btn.dataset.target);
                if (alvo) alvo.textContent = 'Console limpo. Aguardando novos eventos...';
            });
        });
        const nivel = $('t-console-nivel');
        const busca = $('t-console-busca');
        if (nivel) nivel.addEventListener('change', renderConsole);
        if (busca) busca.addEventListener('input', renderConsole);
        const pausar = $('t-console-pausar');
        if (pausar) pausar.addEventListener('click', () => {
            consolePausado = !consolePausado;
            pausar.classList.toggle('active', consolePausado);
            const icon = pausar.querySelector('.material-symbols-outlined');
            if (icon) icon.textContent = consolePausado ? 'play_arrow' : 'pause';
        });
    }

    function init() {
        configurarTemaChart();
        configurarConsoleAcoes();

        const periodo = $('t-periodo');
        if (periodo) periodo.addEventListener('change', () => { janelaMinutos = parseInt(periodo.value, 10) || 0; carregarDashboard(); });

        const auto = $('t-autorefresh');
        if (auto) auto.addEventListener('change', () => { autoRefreshMs = parseInt(auto.value, 10) || 0; reagendarAutoRefresh(); });

        const btnRefresh = $('btn-refresh-telemetria');
        if (btnRefresh) btnRefresh.addEventListener('click', carregarDashboard);

        const busca = $('t-table-search');
        if (busca) busca.addEventListener('input', () => { termoBusca = busca.value.trim().toLowerCase(); paginaAtual = 1; renderizarTabela(); });

        const prev = $('t-page-prev');
        const next = $('t-page-next');
        if (prev) prev.addEventListener('click', () => { if (paginaAtual > 1) { paginaAtual--; renderizarTabela(); } });
        if (next) next.addEventListener('click', () => {
            const total = Math.max(1, Math.ceil(filtrarHistorico().length / ITENS_POR_PAGINA));
            if (paginaAtual < total) { paginaAtual++; renderizarTabela(); }
        });

        carregarDashboard();
        reagendarAutoRefresh();
        window.addEventListener('beforeunload', () => { if (timerAutoRefresh) clearInterval(timerAutoRefresh); });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

(function () {
    const ITENS_POR_PAGINA = 15;
    let historicoCompleto = [];
    let termoBusca = '';
    let paginaAtual = 1;
    let chartModulos;
    let chartNiveis;
    let chartAtividade;
    let timerAutoRefresh;

    const CORES_MODULO = ['#f0b429', '#58a6ff', '#3fb950', '#d29922', '#f85149', '#a371f7', '#39d353', '#ff7b72'];
    const CORES_NIVEL = { INFO: '#58a6ff', WARN: '#d29922', ERROR: '#f85149', DEBUG: '#8b949e' };

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

    function nivelClasse(level) {
        const l = (level || '').toUpperCase();
        if (l === 'ERROR') return 'level-error';
        if (l === 'WARN' || l === 'WARNING') return 'level-warn';
        if (l === 'DEBUG') return 'level-debug';
        return 'level-info';
    }

    function formatarHorario(iso) {
        if (!iso) return '--';
        try {
            const d = new Date(iso);
            return d.toLocaleString('pt-BR');
        } catch (_) {
            return iso;
        }
    }

    const esc = (s) => (window.HtmlEscape && window.HtmlEscape.esc) ? window.HtmlEscape.esc(s) : String(s == null ? '' : s);

    function atualizarMetricas(data) {
        const resumo = data.resumo || {};
        document.getElementById('t-total-eventos').textContent = String(resumo.totalEventos || 0);
        document.getElementById('t-modulos').textContent = String(Object.keys(resumo.contagemPorModulo || {}).length);
        document.getElementById('t-http-ratio').textContent = (data.totalHttpOk || 0) + ' / ' + (data.totalHttpErro || 0);
        document.getElementById('t-latencia').textContent = (data.mediaDuracaoHttpMs || 0) + ' ms';
        const pasta = document.getElementById('t-pasta-logs');
        if (pasta) {
            pasta.textContent = data.pastaLogs ? 'Logs: ' + data.pastaLogs : '';
        }
    }

    function atualizarGraficos(data) {
        const resumo = data.resumo || {};
        const modulos = resumo.contagemPorModulo || {};
        const niveis = resumo.contagemPorNivel || {};
        const atividade = data.atividadePorMinuto || [];

        const labelsMod = Object.keys(modulos);
        const valoresMod = Object.values(modulos);

        if (chartModulos) chartModulos.destroy();
        chartModulos = new Chart(document.getElementById('chart-modulos'), {
            type: 'bar',
            data: {
                labels: labelsMod,
                datasets: [{
                    label: 'Eventos',
                    data: valoresMod,
                    backgroundColor: labelsMod.map((_, i) => CORES_MODULO[i % CORES_MODULO.length]),
                    borderRadius: 4
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });

        const labelsNiv = Object.keys(niveis);
        if (chartNiveis) chartNiveis.destroy();
        chartNiveis = new Chart(document.getElementById('chart-niveis'), {
            type: 'doughnut',
            data: {
                labels: labelsNiv,
                datasets: [{
                    data: Object.values(niveis),
                    backgroundColor: labelsNiv.map(n => CORES_NIVEL[n] || '#8b97ad'),
                    borderColor: '#0a0e16',
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                cutout: '62%',
                plugins: {
                    legend: { position: 'bottom' },
                    tooltip: { usePointStyle: true }
                }
            },
            plugins: [{
                id: 'centerTotal',
                beforeDraw(chart) {
                    const { ctx, chartArea: { width, height } } = chart;
                    const total = (chart.data.datasets[0].data || []).reduce((a, b) => a + b, 0);
                    ctx.save();
                    ctx.font = '600 1.1rem "Space Grotesk", sans-serif';
                    ctx.fillStyle = '#eef2f8';
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'middle';
                    ctx.fillText(String(total), width / 2, height / 2);
                    ctx.restore();
                }
            }]
        });

        const labelsAtv = atividade.map(a => a.minuto);
        const valoresAtv = atividade.map(a => a.total);
        if (chartAtividade) chartAtividade.destroy();
        chartAtividade = new Chart(document.getElementById('chart-atividade'), {
            type: 'line',
            data: {
                labels: labelsAtv,
                datasets: [{
                    label: 'Eventos/min',
                    data: valoresAtv,
                    borderColor: '#f0b429',
                    backgroundColor: 'rgba(240,180,41,0.15)',
                    fill: true,
                    tension: 0.3,
                    pointRadius: 3
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
            }
        });
    }

    function atualizarConsole(linhas) {
        const el = document.getElementById('console-telemetria');
        if (!el) return;
        if (!linhas || linhas.length === 0) {
            el.textContent = 'Nenhuma saída registrada ainda.';
            return;
        }
        el.textContent = linhas.join('\n');
        el.scrollTop = el.scrollHeight;
    }

    function filtrarHistorico() {
        if (!termoBusca) return historicoCompleto;
        const t = termoBusca.toLowerCase();
        return historicoCompleto.filter(e => {
            const msg = [e.evento, e.modulo, e.status, e.message, e.level].join(' ').toLowerCase();
            return msg.includes(t);
        });
    }

    function renderizarTabela() {
        const filtrados = filtrarHistorico();
        const totalPaginas = Math.max(1, Math.ceil(filtrados.length / ITENS_POR_PAGINA));
        if (paginaAtual > totalPaginas) paginaAtual = totalPaginas;
        const inicio = (paginaAtual - 1) * ITENS_POR_PAGINA;
        const pagina = filtrados.slice(inicio, inicio + ITENS_POR_PAGINA);
        const tbody = document.getElementById('t-table-body');
        const count = document.getElementById('t-table-count');
        const info = document.getElementById('t-page-info');

        if (count) count.textContent = filtrados.length + ' evento(s)';
        if (info) info.textContent = 'Página ' + paginaAtual + ' de ' + totalPaginas;

        if (!tbody) return;
        if (pagina.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-muted">Nenhum evento encontrado.</td></tr>';
            return;
        }
        tbody.innerHTML = pagina.map(e => `
            <tr>
                <td>${esc(formatarHorario(e.timestamp))}</td>
                <td class="${nivelClasse(e.level)}">${esc(e.level || '--')}</td>
                <td>${esc(e.modulo || '--')}</td>
                <td>${esc(e.evento || '--')}</td>
                <td>${esc(e.status || '--')}</td>
                <td>${esc(e.message || '')}</td>
            </tr>
        `).join('');
    }

    async function carregarDashboard() {
        try {
            const resp = await fetch('/telemetria/api/dashboard?limit=300&console=250');
            if (!resp.ok) throw new Error('HTTP ' + resp.status);
            const data = await resp.json();
            atualizarMetricas(data);
            atualizarGraficos(data);
            atualizarConsole(data.consoleLinhas);
            historicoCompleto = (data.resumo && data.resumo.ultimosEventos) ? data.resumo.ultimosEventos : [];
            renderizarTabela();
        } catch (err) {
            const el = document.getElementById('console-telemetria');
            if (el) el.textContent = 'Erro ao carregar telemetria: ' + err.message;
        }
    }

    function configurarConsoleAcoes() {
        document.querySelectorAll('.btn-toggle-console').forEach(btn => {
            btn.addEventListener('click', () => {
                const alvo = document.getElementById(btn.dataset.target);
                if (alvo) alvo.classList.toggle('expanded');
            });
        });
        document.querySelectorAll('.btn-copy-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                const alvo = document.getElementById(btn.dataset.target);
                if (!alvo) return;
                try {
                    await navigator.clipboard.writeText(alvo.textContent || '');
                } catch (_) { /* ignore */ }
            });
        });
        document.querySelectorAll('.btn-clear-console').forEach(btn => {
            btn.addEventListener('click', async () => {
                const alvo = document.getElementById(btn.dataset.target);
                try {
                    await fetch('/telemetria/api/console/limpar', { method: 'POST' });
                } catch (_) { /* ignore */ }
                if (alvo) alvo.textContent = 'Console limpo. Aguardando novos eventos...';
            });
        });
    }

    function init() {
        configurarTemaChart();
        configurarConsoleAcoes();

        const btnRefresh = document.getElementById('btn-refresh-telemetria');
        if (btnRefresh) btnRefresh.addEventListener('click', carregarDashboard);

        const busca = document.getElementById('t-table-search');
        if (busca) {
            busca.addEventListener('input', () => {
                termoBusca = busca.value.trim().toLowerCase();
                paginaAtual = 1;
                renderizarTabela();
            });
        }

        const prev = document.getElementById('t-page-prev');
        const next = document.getElementById('t-page-next');
        if (prev) prev.addEventListener('click', () => { if (paginaAtual > 1) { paginaAtual--; renderizarTabela(); } });
        if (next) next.addEventListener('click', () => {
            const total = Math.max(1, Math.ceil(filtrarHistorico().length / ITENS_POR_PAGINA));
            if (paginaAtual < total) { paginaAtual++; renderizarTabela(); }
        });

        carregarDashboard();
        timerAutoRefresh = setInterval(carregarDashboard, 8000);
        window.addEventListener('beforeunload', () => clearInterval(timerAutoRefresh));
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

// Verificação em NAVEGADOR REAL das correções de frontend da auditoria de 2026-09-24 (A6: a prova
// alcança a renderização, não só o HTML). Uso: node verificar-auditoria-frontend.mjs [baseUrl]
// Sai 0 se tudo passou, 1 se algo reprovou, 2 se não conseguiu rodar (servidor fora etc.).
import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8089';
const resultados = [];
const registrar = (id, ok, detalhe) => {
  resultados.push({ id, ok, detalhe });
  console.log(`${ok ? 'PASSOU ' : 'REPROVOU'} ${id} — ${detalhe}`);
};

async function assentar(page) {
  await page.waitForTimeout(900);
}

async function novaPagina(browser, { bloquearFontesGoogle = false, errosConsole } = {}) {
  const ctx = await browser.newContext();
  // Roteiro em volume não toca o Google Tradutor: muitas cargas seguidas levam o IP para o captcha
  // ("sorry") e derrubam a tradução de verdade. A tradução tem roteiro próprio (verificar-tradutor.mjs).
  await ctx.route(/translate\.google(apis)?\.com|translate-pa\.googleapis\.com|www\.google\.com/, (r) => r.abort());
  if (bloquearFontesGoogle) {
    await ctx.route(/fonts\.(googleapis|gstatic)\.com/, (r) => r.abort());
  }
  const page = await ctx.newPage();
  if (errosConsole) {
    page.on('console', (m) => { if (m.type() === 'error') errosConsole.push(m.text()); });
    page.on('pageerror', (e) => errosConsole.push(String(e)));
  }
  return page;
}

let browser;
try {
  browser = await chromium.launch();
  const sonda = await novaPagina(browser);
  const r = await sonda.goto(BASE + '/', { waitUntil: 'domcontentloaded' });
  if (!r || r.status() !== 200) { console.log('NÃO VERIFICOU — servidor não respondeu 200 em', BASE); process.exit(2); }
} catch (e) {
  console.log('NÃO VERIFICOU —', e.message); process.exit(2);
}

// F17 — com o Google Fonts BLOQUEADO, o ícone ainda é glifo (largura de ~1 caractere), não a palavra.
{
  const page = await novaPagina(browser, { bloquearFontesGoogle: true });
  await page.goto(BASE + '/', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.evaluate(() => document.fonts.ready);
  const m = await page.evaluate(() => {
    const el = [...document.querySelectorAll('.material-symbols-outlined')].find((e) => e.textContent.trim() === 'home');
    const r = el.getBoundingClientRect();
    const fs = parseFloat(getComputedStyle(el).fontSize);
    return { largura: r.width, fonte: fs, carregada: document.fonts.check(`${fs}px "Material Symbols Outlined"`) };
  });
  registrar('F17 ícone local com CDN bloqueado', m.carregada && m.largura <= m.fonte * 1.5,
    `fonte carregada=${m.carregada}, largura do "home"=${m.largura.toFixed(1)}px para fonte ${m.fonte}px`);
  await page.context().close();
}

// F18/F19 — no DOM renderizado (inclusive depois de um swap htmx), nenhum ícone/código sem translate=no.
{
  const page = await novaPagina(browser);
  const semCarimbo = async () => page.evaluate(() => [...document.querySelectorAll('.material-symbols-outlined, code, pre')]
    .filter((e) => e.getAttribute('translate') !== 'no').length);
  let total = 0;
  for (const rota of ['/', '/protocolos', '/analise', '/ipv6/analise', '/resolucao-problemas?demo=fiap']) {
    await page.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
    total += await semCarimbo();
  }
  await page.goto(BASE + '/ipv6/analise', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.fill('#ipv6Endereco', '2001:db8::1');
  await Promise.all([page.waitForEvent('response', (x) => x.request().method() === 'POST'),
    page.click('#formCalc button[type="submit"]')]);
  await page.waitForTimeout(400);
  const aposSwap = await semCarimbo();
  const icones = await page.evaluate(() => document.querySelectorAll('.material-symbols-outlined').length);
  registrar('F18/F19 carimbo translate=no no DOM real', total === 0 && aposSwap === 0 && icones > 10,
    `sem carimbo nas páginas=${total}, após swap htmx=${aposSwap}, ícones na tela=${icones}`);
  await page.context().close();
}

// F09 — /informacoes mostra o relatório inicial (antes: vazio, JSON.parse falhava em silêncio).
{
  const erros = [];
  const page = await novaPagina(browser, { errosConsole: erros });
  await page.goto(BASE + '/informacoes?ip=127.0.0.1', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const txt = (await page.textContent('#geo-report-root')) || '';
  registrar('F09 relatório inicial de /informacoes', txt.trim().length > 20 && !erros.some((e) => /geo-initial-payload/.test(e)),
    `texto no relatório=${txt.trim().length} caracteres`);
  await page.context().close();
}

// F11 — detalhes do OSPF mostram os marcadores da sintaxe (<PROCESS_ID>), e não os apagam.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/protocolos', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const busca = page.locator('[data-grid-search="protocolos"]').first();
  if (await busca.count()) {
    await busca.fill('OSPF');
    await page.waitForTimeout(400);
  }
  const linha = page.locator('[data-grid-row="protocolos"][data-sintaxe*="PROCESS_ID"]:visible').first();
  const existe = await linha.count();
  if (!existe) {
    registrar('F11 sintaxe do catálogo', false, 'nenhuma linha visível com PROCESS_ID encontrada (instrumento cego)');
  } else {
    await linha.locator('[data-grid-details="protocolos"]').click();
    await page.waitForTimeout(300);
    const corpo = await page.evaluate(() => {
      const b = [...document.querySelectorAll('.modal.show .modal-body, .modal-body')].find((x) => x.textContent.includes('Sintaxe'));
      return b ? b.textContent : '';
    });
    registrar('F11 sintaxe do catálogo', corpo.includes('<PROCESS_ID>'), `trecho: ${corpo.replace(/\s+/g, ' ').slice(0, 120)}`);
  }
  await page.context().close();
}

// F10 — clicar num nó do diagrama da Resolução abre os detalhes.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/resolucao-problemas?demo=fiap', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  // O demo só preenche o formulário: o diagrama nasce ao calcular o cenário.
  await Promise.all([page.waitForNavigation({ waitUntil: 'load' }),
    page.click('button[name="action_type"][value="calculate"]')]);
  await assentar(page);
  await page.waitForSelector('.topology-wrap svg g.node[data-clique-ligado="1"]', { timeout: 15000 }).catch(() => {});
  const antes = (await page.textContent('#topology-detail-box')) || '';
  const no = page.locator('.topology-wrap svg g.node[data-clique-ligado="1"]').first();
  if (!(await no.count())) {
    registrar('F10 clique no diagrama', false, 'nenhum nó com clique ligado');
  } else {
    await no.click();
    const depois = (await page.textContent('#topology-detail-box')) || '';
    registrar('F10 clique no diagrama', depois.trim() !== antes.trim() && !/Clique em um roteador/.test(depois),
      `detalhe: ${depois.replace(/\s+/g, ' ').trim().slice(0, 100)}`);
  }
  await page.context().close();
}

// F13 — máscara não contígua é recusada no navegador (como no servidor).
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/analise?tab=wildcard', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const campo = page.locator('#wild_mask_input');
  if (!(await campo.count())) {
    registrar('F13 widget de máscara', false, 'campo #wild_mask_input não encontrado');
  } else {
    await page.evaluate(() => { const i = document.getElementById('wild_mask_input'); i.value = '255.0.255.0'; i.dispatchEvent(new Event('input')); });
    const meta = (await page.textContent('#wild_convert_meta')) || '';
    await page.evaluate(() => { const i = document.getElementById('wild_mask_input'); i.value = '255.255.255.0'; i.dispatchEvent(new Event('input')); });
    const ok = (await page.textContent('#wild_convert_meta')) || '';
    registrar('F13 widget de máscara', /não contígua/.test(meta) && /\/24/.test(ok),
      `255.0.255.0 → "${meta.trim().slice(0, 60)}" | controle 255.255.255.0 → "${ok.trim().slice(0, 40)}"`);
  }
  await page.context().close();
}

// F14 — 403 numa requisição htmx vira aviso visível e tira da tela o resultado anterior.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/ipv6/analise', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.fill('#ipv6Endereco', '2001:db8::1');
  await Promise.all([page.waitForEvent('response', (x) => x.request().method() === 'POST'),
    page.click('#formCalc button[type="submit"]')]);
  await page.waitForTimeout(300);
  await page.route(/\/ipv6\/api\//, (r) => r.fulfill({ status: 403, body: 'CSRF' }));
  await page.fill('#ipv6Endereco', '2001:db8::2');
  await Promise.all([page.waitForEvent('response', (x) => x.status() === 403),
    page.click('#formCalc button[type="submit"]')]);
  await page.waitForTimeout(300);
  const saida = (await page.textContent('#saidaCalc')) || '';
  registrar('F14 erro htmx visível', /proteção do formulário expirou/.test(saida) && !saida.includes('2001:db8::1'),
    `alvo agora: ${saida.replace(/\s+/g, ' ').trim().slice(0, 100)}`);
  await page.context().close();
}

// F12 — export e histórico usam o endereço ANALISADO, não o que está no campo depois.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/ipv6/analise', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.evaluate(() => { try { localStorage.clear(); } catch (e) { /* */ } });
  await page.fill('#ipv6Endereco', '2001:db8::a');
  await Promise.all([page.waitForEvent('response', (x) => x.request().method() === 'POST'),
    page.click('#formCalc button[type="submit"]')]);
  await page.waitForTimeout(400);
  await page.fill('#ipv6Endereco', '2001:db8::b');
  const href = await page.getAttribute('#ipv6-export-json', 'href');
  const hist = await page.evaluate(() => JSON.stringify(Object.fromEntries(Object.entries(localStorage))));
  registrar('F12 export/histórico do endereço analisado',
    decodeURIComponent(href || '').includes('2001:db8::a') && hist.includes('2001:db8::a') && !hist.includes('2001:db8::b'),
    `href=${href}`);
  await page.context().close();
}

// F20 — bibliotecas de CDN com SRI carregam de fato (hash errado bloquearia o script).
{
  for (const [rota, global] of [['/protocolos/bgp', 'mermaid'], ['/ipv6/resolucao', 'mermaid'],
    ['/resolucao-problemas?demo=fiap', 'mermaid'], ['/localizacao', 'L']]) {
    const erros = [];
    const page = await novaPagina(browser, { errosConsole: erros });
    await page.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
    const definido = await page.evaluate((g) => typeof window[g] !== 'undefined', global);
    const integridade = erros.filter((e) => /integrity|digest/i.test(e));
    registrar(`F20 SRI em ${rota}`, definido && integridade.length === 0,
      `window.${global} definido=${definido}; erros de integridade=${integridade.length}`);
    await page.context().close();
  }
}

// F32 — pilha dupla (servidor vê IPv4, WebRTC mostra IPv6) não é acusada como VPN.
{
  const page = await novaPagina(browser);
  await page.addInitScript(() => {
    class PCFalso {
      constructor() { this.onicecandidate = null; }
      createDataChannel() { return {}; }
      createOffer() { return Promise.resolve({ type: 'offer', sdp: '' }); }
      setLocalDescription() {
        setTimeout(() => this.onicecandidate && this.onicecandidate({
          candidate: { candidate: 'candidate:1 1 udp 1 2001:db8::99 5000 typ srflx', address: '2001:db8::99', type: 'srflx' },
        }), 20);
        return Promise.resolve();
      }
      close() {}
    }
    window.RTCPeerConnection = PCFalso;
  });
  await page.route(/\/localizacao\/api\/inspecao/, (r) => r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify({ ipReal: '203.0.113.7', ipConexao: '203.0.113.7', atrasDeProxy: false }),
  }));
  await page.goto(BASE + '/localizacao', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.click('[data-tab="privacidade"]');
  await page.click('#btn-priv-testar');
  await page.waitForSelector('#priv-resultado:not(.d-none)', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(3500);
  const tela = (await page.textContent('body')) || '';
  registrar('F32 pilha dupla não vira VPN', !/Divergência de IP público/.test(tela) && /Pilha dupla/.test(tela),
    `divergência acusada=${/Divergência de IP público/.test(tela)}, nota de pilha dupla=${/Pilha dupla/.test(tela)}`);
  await page.context().close();
}

// BF4 (revisão de boa-fé) — 429 no CEP vira aviso de erro, não "Endereço por CEP" com campos vazios.
// Controle (A1): a resposta 200 legítima continua desenhando a ficha do endereço.
{
  const page = await novaPagina(browser);
  const buscar = async (status, corpo) => {
    await page.unroute(/\/localizacao\/api\/cep/).catch(() => {});
    await page.route(/\/localizacao\/api\/cep/, (r) => r.fulfill({
      status, contentType: 'application/json', body: JSON.stringify(corpo),
    }));
    await page.fill('#loc-cep', '07062031');
    await page.press('#loc-cep', 'Enter');
    await page.waitForTimeout(600);
    return (await page.textContent('#loc-resultado')) || '';
  };
  await page.goto(BASE + '/localizacao', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.click('[data-tab="cep"]');
  const com429 = await buscar(429, { erro: 'Muitas requisições. Aguarde um minuto e tente novamente.' });
  const com200 = await buscar(200, { ok: true, cep: '07062-031', logradouro: 'Rua Laura', cidade: 'Guarulhos', uf: 'SP',
    geocoded: false, aviso: 'sem mapa' });
  registrar('BF4 429 no CEP vira aviso', /CEP não localizado/.test(com429) && /Muitas requisições/.test(com429)
      && !/Endereço por CEP/.test(com429) && /Endereço por CEP/.test(com200) && /Rua Laura/.test(com200),
    `429 → "${com429.trim().slice(0, 70)}" | 200 → "${com200.trim().slice(0, 40)}"`);
  await page.context().close();
}

// F31 — "Limpar console" só diz "limpo" se o servidor limpou (403 → mensagem honesta).
// Login com a chave do perfil DEV (pública no application.properties; não existe em produção).
{
  const chaveDev = process.env.CHAVE_ADMIN_DEV || 'dev-admin-key-local';
  const page = await novaPagina(browser);
  await page.goto(BASE + '/login/?modo=contingencia', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.fill('#adminKey', chaveDev);
  await Promise.all([page.waitForNavigation({ waitUntil: 'load' }), page.click('form[action="/login/chave"] button[type="submit"]')]);
  await page.goto(BASE + '/telemetria/', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const botao = page.locator('.btn-clear-console').first();
  if (!(await botao.count())) {
    registrar('F31 limpar console honesto', false, 'botão não encontrado (login falhou?) — url=' + page.url());
  } else {
    await page.route(/\/telemetria\/api\/console\/limpar/, (r) => r.fulfill({ status: 403, body: '' }));
    await botao.click();
    await page.waitForTimeout(400);
    const com403 = (await page.textContent('#console-telemetria')) || '';
    await page.unroute(/\/telemetria\/api\/console\/limpar/);
    await page.route(/\/telemetria\/api\/console\/limpar/, (r) => r.fulfill({ status: 204, body: '' }));
    await botao.click();
    await page.waitForTimeout(400);
    const com204 = (await page.textContent('#console-telemetria')) || '';
    registrar('F31 limpar console honesto', /Não limpo/.test(com403) && !/Console limpo/.test(com403) && /Console limpo/.test(com204),
      `403 → "${com403.trim().slice(0, 60)}" | 204 → "${com204.trim().slice(0, 30)}"`);
  }
  await page.context().close();
}

// A11Y-01 (auditoria de 2026-10-01) — o tooltip do Bootstrap trocava o aria-describedby do campo pelo id
// dele e o APAGAVA ao esconder: a dica própria do campo sumia para o leitor de tela depois do 1º foco.
for (const caso of [
  { rota: '/ferramentas', campo: '#cmd-alvo', descr: 'cmd-alvo-dica' },
  { rota: '/laboratorios/camadas', campo: '[aria-describedby="lab-msg-ajuda"]', descr: 'lab-msg-ajuda' },
]) {
  const page = await novaPagina(browser);
  await page.goto(BASE + caso.rota, { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const el = await page.$(caso.campo);
  const ids = async () => ((await el.getAttribute('aria-describedby')) || '').split(/\s+/).filter(Boolean);
  await el.focus();
  await page.waitForTimeout(400);
  const durante = await ids();
  await page.evaluate(() => document.activeElement && document.activeElement.blur());
  await page.waitForTimeout(400);
  const depois = await ids();
  registrar(`A11Y-01 descrição do campo sobrevive ao tooltip (${caso.rota})`,
    durante.some((i) => i.startsWith('tooltip')) && durante.includes(caso.descr) && depois.join(' ') === caso.descr,
    `durante="${durante.join(' ')}" depois="${depois.join(' ')}"`);
  await page.context().close();
}

// FRONT-04 (auditoria de 2026-10-01) — o Mermaid 11 trocava o diagrama inválido por "Syntax error in text";
// o fallback (definição em texto) nunca aparecia. Injeta um ";" na mensagem e confere texto + aviso.
{
  const page = await novaPagina(browser);
  await page.route(/\/camadas\/dispositivos$/, async (r) => {
    const resp = await r.fetch();
    const html = (await resp.text()).replace('Checa porta e estado. Se negado, descarta', 'Checa porta e estado; se negado, descarta');
    await r.fulfill({ response: resp, body: html });
  });
  await page.goto(BASE + '/camadas/dispositivos', { waitUntil: 'load', timeout: 45000 });
  await page.waitForTimeout(2500);
  const m = await page.evaluate(() => ({
    erro: /Syntax error|Parse error/i.test(document.body.innerText),
    aviso: !!document.querySelector('.aprof-mermaid-aviso'),
    texto: /se negado/.test((document.querySelector('.aprof-mermaid') || {}).textContent || ''),
  }));
  registrar('FRONT-04 diagrama inválido cai no texto', !m.erro && m.aviso && m.texto,
    `syntax error na tela=${m.erro}, aviso=${m.aviso}, definição em texto=${m.texto}`);
  await page.context().close();
}

// FRONT-09 (auditoria de 2026-10-01) — "outline: 0.156%" é CSS inválido (outline-width não aceita %) e o
// tema zera o box-shadow do cartão com !important: o foco por TECLADO não aparecia nos cartões da home.
// Navega com Tab (só o teclado ativa :focus-visible) e mede o contorno calculado.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const medir = () => page.evaluate(() => {
    const el = document.activeElement;
    const cs = el ? getComputedStyle(el) : null;
    return el ? { cartao: el.classList.contains('home-module'), nav: el.classList.contains('aed-nav-link'),
      estilo: cs.outlineStyle, largura: parseFloat(cs.outlineWidth) || 0 } : {};
  });
  const achados = { cartao: null, nav: null };
  for (let i = 0; i < 120 && (!achados.cartao || !achados.nav); i++) {
    await page.keyboard.press('Tab');
    const m = await medir();
    if (m.cartao && !achados.cartao) achados.cartao = m;
    if (m.nav && !achados.nav) achados.nav = m;
  }
  const visivel = (m) => m && m.estilo !== 'none' && m.largura >= 2;
  registrar('FRONT-09 foco por teclado visível (cartão da home e link do menu)',
    visivel(achados.cartao) && visivel(achados.nav),
    `cartão=${JSON.stringify(achados.cartao)} menu=${JSON.stringify(achados.nav)}`);
  await page.context().close();
}

// FRONT-02/12 (auditoria de 2026-10-01) — a dica automática trazia o nome do glifo ("lan Bloco base") ou o
// rótulo de outro campo, e o Bootstrap a copiava para o aria-label (nome acessível). Re-init dinâmico
// recriava o title e a dica nativa aparecia junto da do Bootstrap.
{
  const page = await novaPagina(browser);
  const achados = [];
  for (const rota of ['/calculadora', '/seguranca', '/analise', '/ipv6']) {
    await page.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
    await assentar(page);
    const r = await page.evaluate(() => {
      const glifos = new Set([...document.querySelectorAll('.material-symbols-outlined')].map((e) => e.textContent.trim()).filter(Boolean));
      const comGlifo = (t) => !!t && [...glifos].some((g) => t === g || t.startsWith(g + ' '));
      const ruins = [];
      document.querySelectorAll('input, select, textarea').forEach((el) => {
        const dica = el.getAttribute('data-bs-original-title') || el.getAttribute('title') || '';
        const aria = el.getAttribute('aria-label') || '';
        if (comGlifo(dica) || comGlifo(aria)) ruins.push((el.id || el.name || el.tagName) + ' dica="' + dica + '" aria="' + aria + '"');
        if (el.labels && el.labels.length && aria && aria === dica && !el.hasAttribute('data-aria-proprio')) {
          ruins.push((el.id || el.name) + ' aria-label sobrescrito pela dica');
        }
      });
      if (window.FieldTooltips) window.FieldTooltips.init(document);
      const duplas = [...document.querySelectorAll('[data-bs-original-title]')]
        .filter((el) => (el.getAttribute('title') || '').trim() && window.bootstrap.Tooltip.getInstance(el)).length;
      return { ruins, duplas };
    });
    r.ruins.forEach((x) => achados.push(rota + ': ' + x));
    if (r.duplas) achados.push(rota + ': ' + r.duplas + ' com dica nativa e Bootstrap juntas');
  }
  registrar('FRONT-02/12 dica sem glifo, nome acessível do rótulo, sem dica dupla', achados.length === 0,
    achados.length ? achados.slice(0, 6).join(' | ') : '4 páginas limpas');
  await page.context().close();
}

// FRONT-10 — Esc fecha a dica aberta (WCAG 1.4.13).
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/calculadora', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const alvo = await page.evaluate(() => {
    const el = [...document.querySelectorAll('[data-bs-toggle="tooltip"]')].find((e) => e.offsetParent !== null && /INPUT|SELECT|TEXTAREA|BUTTON/.test(e.tagName));
    if (!el) return null;
    el.setAttribute('data-verif', 'esc');
    return true;
  });
  let aberta = false, depois = true;
  if (alvo) {
    await page.focus('[data-verif="esc"]');
    await page.waitForTimeout(400);
    aberta = await page.evaluate(() => !!document.querySelector('.tooltip.show'));
    await page.keyboard.press('Escape');
    await page.waitForTimeout(400);
    depois = await page.evaluate(() => !!document.querySelector('.tooltip.show'));
  }
  registrar('FRONT-10 Esc fecha a dica', !!alvo && aberta && !depois, `alvo=${!!alvo} aberta no foco=${aberta} aberta depois do Esc=${depois}`);
  await page.context().close();
}

// FRONT-08 — toda aba role="tab" diz se está selecionada, e o valor acompanha a troca.
{
  const page = await novaPagina(browser);
  const achados = [];
  for (const rota of ['/analise', '/seguranca', '/trafego', '/diagnostico', '/localizacao', '/ipv6']) {
    await page.goto(BASE + rota, { waitUntil: 'load', timeout: 45000 });
    await assentar(page);
    const r = await page.evaluate(() => {
      const abas = [...document.querySelectorAll('[role="tab"]')];
      const sem = abas.filter((a) => !['true', 'false'].includes(a.getAttribute('aria-selected'))).length;
      const verdadeiras = abas.filter((a) => a.getAttribute('aria-selected') === 'true').length;
      const outra = abas.find((a) => a.getAttribute('aria-selected') === 'false' && a.offsetParent !== null);
      if (outra) outra.setAttribute('data-verif', 'aba');
      return { total: abas.length, sem, verdadeiras, temOutra: !!outra };
    });
    let trocou = true;
    if (r.temOutra) {
      await page.click('[data-verif="aba"]');
      await page.waitForTimeout(200);
      trocou = await page.evaluate(() => document.querySelector('[data-verif="aba"]').getAttribute('aria-selected') === 'true');
    }
    if (!r.total || r.sem || !r.verdadeiras || !trocou) achados.push(`${rota}: ${JSON.stringify(r)} trocou=${trocou}`);
  }
  registrar('FRONT-08 aria-selected nas abas e na troca', achados.length === 0, achados.length ? achados.join(' | ') : '6 páginas');
  await page.context().close();
}

// FRONT-07 — valor técnico pintado depois do carregamento ganha translate="no"; texto humano não.
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/calculadora', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const r = await page.evaluate(async () => {
    const box = document.createElement('div');
    box.innerHTML = '<table><tr><td id="v1">10.0.0.0/24</td><td id="v2">00:1A:2B:3C:4D:5E</td>'
      + '<td id="v3">2001:db8::/48</td><td id="v4">permit tcp any any eq 80</td>'
      + '<td id="h1">Rede da sala de aula</td><td id="h2">permite 10 hosts por rede</td></tr></table>';
    document.body.appendChild(box);
    await new Promise((ok) => setTimeout(ok, 150));
    const t = (id) => document.getElementById(id).getAttribute('translate');
    const res = { v1: t('v1'), v2: t('v2'), v3: t('v3'), v4: t('v4'), h1: t('h1'), h2: t('h2') };
    box.remove();
    return res;
  });
  const ok = ['v1', 'v2', 'v3', 'v4'].every((k) => r[k] === 'no') && r.h1 !== 'no' && r.h2 !== 'no';
  registrar('FRONT-07 valor técnico protegido, texto humano traduzível', ok, JSON.stringify(r));
  await page.context().close();
}

// OPS-06 — o ao vivo consulta a cada 2 s e para com a aba oculta.
{
  const page = await novaPagina(browser);
  let consultas = 0;
  page.on('request', (q) => { if (q.url().includes('/trafego/api/aovivo')) consultas++; });
  await page.goto(BASE + '/trafego', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const tem = await page.evaluate(() => !!document.getElementById('live-start'));
  let visivel = 0, oculta = 0, voltou = 0;
  if (tem) {
    await page.evaluate(() => document.getElementById('live-start').click());
    consultas = 0; await page.waitForTimeout(4500); visivel = consultas;
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => true }); document.dispatchEvent(new Event('visibilitychange')); });
    consultas = 0; await page.waitForTimeout(4500); oculta = consultas;
    await page.evaluate(() => { Object.defineProperty(document, 'hidden', { configurable: true, get: () => false }); document.dispatchEvent(new Event('visibilitychange')); });
    consultas = 0; await page.waitForTimeout(2500); voltou = consultas;
  }
  registrar('OPS-06 ao vivo pausa com a aba oculta', tem && visivel >= 2 && visivel <= 3 && oculta === 0 && voltou >= 1,
    `em 4,5 s: visível=${visivel} oculta=${oculta}; ao voltar=${voltou}`);
  await page.context().close();
}

// ACAD-06 — Enter duas vezes na mesma resposta fora da faixa não conta de novo (nem revela o gabarito).
{
  const page = await novaPagina(browser);
  await page.goto(BASE + '/academia/fundamentos/binario', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  await page.fill('#provar-resposta', '111111111');
  await page.press('#provar-resposta', 'Enter');
  await page.waitForTimeout(200);
  const primeira = await page.textContent('.acad-feedback');
  await page.press('#provar-resposta', 'Enter');
  await page.waitForTimeout(200);
  const segunda = await page.textContent('.acad-feedback');
  const ok = /não contou de novo/.test(segunda) && !/Resposta:/.test(segunda);
  registrar('ACAD-06 resposta fora da faixa repetida não conta', ok, `1ª="${(primeira || '').trim().slice(0, 60)}" 2ª="${(segunda || '').trim().slice(0, 60)}"`);
  await page.context().close();
}

// ACAD-03 — o resumo da visita sai uma vez, com o estado acumulado: trocar de aba e voltar não envia.
{
  const page = await novaPagina(browser);
  const visitas = [];
  page.on('request', (q) => {
    if (q.url().includes('/academia/api/eventos') && q.method() === 'POST') {
      try { const c = JSON.parse(q.postData() || '{}'); if (c.tipo === 'visita') visitas.push(c); } catch (e) { /* ignora */ }
    }
  });
  await page.goto(BASE + '/academia/fundamentos/binario', { waitUntil: 'load', timeout: 45000 });
  await assentar(page);
  const mudar = (estado) => page.evaluate((e) => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => e });
    document.dispatchEvent(new Event('visibilitychange'));
  }, estado);
  await page.evaluate(() => window.AcademiaSinais._definirPrazoOculta && window.AcademiaSinais._definirPrazoOculta(1500));
  await mudar('hidden'); await page.waitForTimeout(300); await mudar('visible');
  await page.waitForTimeout(2200);
  const aposVoltar = visitas.length;
  await mudar('hidden'); await page.waitForTimeout(2500);
  const aposAbandono = visitas.length;
  await page.evaluate(() => window.dispatchEvent(new Event('pagehide')));
  await page.waitForTimeout(400);
  const final = visitas.length;
  registrar('ACAD-03 visita enviada uma vez, só ao abandonar', aposVoltar === 0 && aposAbandono === 1 && final === 1,
    `troca rápida=${aposVoltar} aba abandonada=${aposAbandono} depois do pagehide=${final} segundos=${visitas[0] ? visitas[0].segundos : '-'}`);
  await page.context().close();
}

await browser.close();
const reprovados = resultados.filter((r) => !r.ok);
console.log(`\n${resultados.length - reprovados.length}/${resultados.length} passaram`);
process.exit(reprovados.length ? 1 : 0);

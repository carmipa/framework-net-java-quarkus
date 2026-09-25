/**
 * Verifica, num browser real, se o Content-Security-Policy nao esta bloqueando
 * recurso legitimo do site.
 *
 * PROPOSITO: um CSP mal calibrado nao gera erro no servidor — a pagina responde
 * 200 e o recurso simplesmente nao carrega. So o browser revela a violacao.
 *
 * USO:  node scripts/verificar-csp.mjs [base-url]
 * SAIDA: 0 ok · 1 violacao de CSP, requisicao bloqueada ou status errado · 2 NAO VERIFICOU
 *        (sem sitemap, sem cabecalho CSP ou instrumento incapaz de ver uma violacao proposital).
 */
import { chromium } from 'playwright';

const base = process.argv[2] || 'https://frameworknet.carminati.dev.br';

// Paginas: TODAS as do sitemap (auditoria F26 — a lista fixa nao tinha IPv6, Laboratorios,
// Certificados, Camadas, Criptografia, Wi-Fi nem Ferramentas) mais as que o sitemap nao lista.
const EXTRAS = [
  '/resolucao-problemas?aba=reversa&demo=bgp',
  '/login/?modo=contingencia',
  // Pagina de erro: carrega Google Fonts, bandeiras do flagcdn e o Translate.
  // E justamente a tela onde ninguem repara se um recurso for bloqueado.
  '/rota-inexistente-para-verificar-csp',
];

async function paginasDoSitemap() {
  const r = await fetch(base + '/sitemap.xml');
  if (!r.ok) return null;
  const xml = await r.text();
  const caminhos = [...xml.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => new URL(m[1]).pathname);
  return caminhos.length ? caminhos : null;
}

const doSitemap = await paginasDoSitemap();
if (!doSitemap) {
  console.log('NAO VERIFICOU: sitemap.xml indisponivel ou vazio em ' + base);
  process.exit(2);
}
const PAGINAS = [...new Set([...doSitemap, ...EXTRAS])];
console.log(`${PAGINAS.length} paginas (${doSitemap.length} do sitemap)`);

const navegador = await chromium.launch();
let problemas = 0;
let ambientais = 0;

// Controle positivo (0 nao e prova): sem cabecalho CSP, "nenhum bloqueio" seria cegueira. E o
// instrumento precisa ENXERGAR uma violacao de verdade: um script de origem proibida tem de ser recusado.
{
  const contexto = await navegador.newContext();
  const pagina = await contexto.newPage();
  const resp = await pagina.goto(base + '/', { waitUntil: 'load', timeout: 45000 });
  const csp = resp && resp.headers()['content-security-policy'];
  if (!csp) {
    console.log('NAO VERIFICOU: a resposta de / nao traz Content-Security-Policy');
    process.exit(2);
  }
  let viuViolacao = false;
  pagina.on('console', (msg) => {
    if (/Content Security Policy|Refused to load/i.test(msg.text())) viuViolacao = true;
  });
  await pagina.evaluate(() => {
    const s = document.createElement('script');
    s.src = 'https://origem-proibida.invalid/sonda.js';
    document.head.appendChild(s);
  });
  await pagina.waitForTimeout(1500);
  await contexto.close();
  if (!viuViolacao) {
    console.log('NAO VERIFICOU: o script de origem proibida nao gerou violacao visivel (instrumento cego)');
    process.exit(2);
  }
  console.log('controle positivo: CSP presente e violacao proposital detectada');
}

for (const caminho of PAGINAS) {
  const contexto = await navegador.newContext();
  const pagina = await contexto.newPage();
  const violacoes = [];
  const falhas = [];

  pagina.on('console', (msg) => {
    const texto = msg.text();
    if (/Content Security Policy|Refused to (load|execute|apply|connect)/i.test(texto)) {
      violacoes.push(texto.slice(0, 180));
    }
  });
  pagina.on('requestfailed', (req) => {
    const motivo = req.failure()?.errorText || '';
    // ERR_BLOCKED_BY_CSP e o sintoma direto; os demais podem ser rede.
    if (/csp|blocked/i.test(motivo)) {
      falhas.push(`${motivo} ${req.url().slice(0, 110)}`);
    }
  });

  try {
    const resp = await pagina.goto(base + caminho, { waitUntil: 'load', timeout: 45000 });
    await pagina.waitForTimeout(1500);
    const esperado = caminho.startsWith('/rota-inexistente') ? 404 : 200;
    if (!resp || resp.status() !== esperado) {
      falhas.push(`status ${resp ? resp.status() : 'sem resposta'} (esperado ${esperado})`);
    }
  } catch (erro) {
    console.log(`  ${caminho.padEnd(22)} ERRO DE NAVEGACAO: ${String(erro).slice(0, 90)}`);
    problemas++;
    await contexto.close();
    continue;
  }

  // Google anti-robô: depois de muitas cargas automatizadas o element.js do Translate redireciona
  // para google.com/sorry (captcha) e o CSP recusa, corretamente. Não é recurso legítimo bloqueado:
  // é ambiente. Fica registrado com a causa, mas não conta como defeito de CSP.
  const ambiental = (t) => /google\.com\/sorry/.test(t);
  const antiRobo = [...violacoes, ...falhas].filter(ambiental).length;
  const reais = { v: violacoes.filter((t) => !ambiental(t)), f: falhas.filter((t) => !ambiental(t)) };
  violacoes.length = 0; violacoes.push(...reais.v);
  falhas.length = 0; falhas.push(...reais.f);
  if (antiRobo) {
    ambientais += antiRobo;
  }
  const total = violacoes.length + falhas.length;
  if (total === 0) {
    console.log(`  ${caminho.padEnd(22)} ok${antiRobo ? '  (Google anti-robô no Translate: ambiental)' : ''}`);
  } else {
    problemas += total;
    console.log(`  ${caminho.padEnd(22)} ${total} VIOLACAO(OES):`);
    for (const v of [...new Set([...violacoes, ...falhas])].slice(0, 6)) {
      console.log(`      ${v}`);
    }
  }
  await contexto.close();
}

await navegador.close();
if (ambientais) {
  console.log(`
${ambientais} bloqueio(s) do redirect anti-robô do Google (ambiental, não é defeito do CSP).`);
}
console.log(problemas === 0
  ? '\nCSP nao bloqueou nenhum recurso legitimo.'
  : `\n${problemas} problema(s) — ajuste o CSP em application.properties.`);
process.exit(problemas === 0 ? 0 : 1);

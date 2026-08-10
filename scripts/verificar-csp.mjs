/**
 * Verifica, num browser real, se o Content-Security-Policy nao esta bloqueando
 * recurso legitimo do site.
 *
 * PROPOSITO: um CSP mal calibrado nao gera erro no servidor — a pagina responde
 * 200 e o recurso simplesmente nao carrega. So o browser revela a violacao.
 *
 * USO:  node scripts/verificar-csp.mjs [base-url]
 * SAIDA: codigo 1 se houver violacao de CSP ou requisicao falhada.
 */
import { chromium } from 'playwright';

const base = process.argv[2] || 'https://frameworknet.carminati.dev.br';

const PAGINAS = [
  '/', '/analise', '/calculadora', '/localizacao', '/documentacao',
  '/portas', '/protocolos', '/protocolos/bgp', '/protocolos/ssh',
  '/resolucao-problemas', '/resolucao-problemas?aba=reversa&demo=bgp', '/trafego',
  '/diagnostico', '/seguranca', '/sobre',
];

const navegador = await chromium.launch();
let problemas = 0;

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
    await pagina.goto(base + caminho, { waitUntil: 'networkidle', timeout: 45000 });
    await pagina.waitForTimeout(1500);
  } catch (erro) {
    console.log(`  ${caminho.padEnd(22)} ERRO DE NAVEGACAO: ${String(erro).slice(0, 90)}`);
    problemas++;
    await contexto.close();
    continue;
  }

  const total = violacoes.length + falhas.length;
  if (total === 0) {
    console.log(`  ${caminho.padEnd(22)} ok`);
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
console.log(problemas === 0
  ? '\nCSP nao bloqueou nenhum recurso legitimo.'
  : `\n${problemas} problema(s) — ajuste o CSP em application.properties.`);
process.exit(problemas === 0 ? 0 : 1);

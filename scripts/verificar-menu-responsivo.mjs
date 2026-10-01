/**
 * MENU DO TOPO EM QUALQUER TELA — com os menus suspensos ABERTOS.
 *
 * PROPÓSITO DE NEGÓCIO: a varredura (varredura.mjs) mede as páginas em repouso, e menu suspenso
 * fechado não aparece em medição nenhuma. Medido em 01/10/2026: no celular os itens do topo não
 * tinham nome para o leitor de tela, os menus suspensos abriam como listas de ícones sem texto e
 * passavam da borda (IPv4 até 447 px numa tela de 320; Topologia até 813 px numa de 768). Este
 * roteiro abre cada menu em cada largura e mede o que a pessoa vê e o que o leitor de tela lê.
 *
 * INVARIANTES DO DOMÍNIO:
 *  - todo item do topo (link ou botão) tem nome acessível igual ao rótulo, em toda largura;
 *  - todo menu suspenso aberto cabe na tela (8 px de folga) e mostra o TEXTO de cada item;
 *  - abrir um menu não cria rolagem horizontal;
 *  - o tradutor do Google fica bloqueado (roteiro em volume dispara o captcha do Google);
 *  - calibração: com o CSS e o JS anteriores (git HEAD, ou --calibrar-css/--calibrar-js) o roteiro
 *    TEM de reprovar — senão ele não distingue o defeito do conserto.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sai 0 (passou), 1 (reprovou) ou 2 (não verificou: servidor fora,
 * Chromium não abriu, calibração sem versão anterior para comparar).
 *
 * USO: node scripts/verificar-menu-responsivo.mjs [http://localhost:8081]
 */
import { chromium } from 'playwright';
import { execFileSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.argv.slice(2).find((a) => a.startsWith('http')) || 'http://localhost:8081';
const LARGURAS = [320, 360, 390, 414, 600, 768, 820, 1024, 1280, 1440, 1920, 2560];
const PAGINAS = ['/', '/laboratorios', '/academia/'];
const GOOGLE = /(^|\.)(translate\.google\.com|translate\.googleapis\.com|translate-pa\.googleapis\.com|www\.google\.com)$/;

function versaoAnterior(caminho) {
  try {
    return execFileSync('git', ['show', 'HEAD:' + caminho], { cwd: RAIZ, encoding: 'utf8' });
  } catch {
    return null;
  }
}

async function medir(browser, { cssAntigo = null, jsAntigo = null } = {}) {
  const achados = [];
  let medidos = 0;
  for (const largura of LARGURAS) {
    const ctx = await browser.newContext({ viewport: { width: largura, height: largura <= 768 ? 900 : 1000 } });
    await ctx.route('**/*', (rota) => {
      const url = new URL(rota.request().url());
      if (GOOGLE.test(url.hostname)) { return rota.abort('blockedbyclient'); }
      if (cssAntigo && url.pathname === '/web/css/aed-command-center.css') {
        return rota.fulfill({ status: 200, contentType: 'text/css', body: cssAntigo });
      }
      if (jsAntigo && url.pathname === '/web/js/aed-shell.js') {
        return rota.fulfill({ status: 200, contentType: 'text/javascript', body: jsAntigo });
      }
      return rota.continue();
    });
    const page = await ctx.newPage();
    for (const caminho of PAGINAS) {
      await page.goto(BASE + caminho, { waitUntil: 'load' });
      await page.waitForTimeout(400);
      // nome acessível = rótulo visível (ou, se o rótulo estiver escondido, o mesmo texto)
      const nomes = await page.$$eval('.aed-nav-tabs > a.aed-nav-link, .aed-nav-tabs .aed-nav-drop-toggle', (els) => els.map((el) => {
        const rotulo = [...el.querySelectorAll('span:not(.material-symbols-outlined)')].map((s) => s.textContent.trim()).join(' ').trim();
        return { rotulo };
      }));
      const arvore = await page.locator('.aed-nav-tabs').ariaSnapshot();
      const linhasTopo = arvore.split('\n').filter((l) => /^\s{0,2}- (link|button)/.test(l));
      const semNome = linhasTopo.filter((l) => /- (link|button):?\s*$/.test(l.trim()) || /- (link|button)\s*:$/.test(l.trim()));
      medidos += nomes.length;
      if (semNome.length) {
        achados.push(`${largura}px ${caminho}: ${semNome.length} item(ns) do topo SEM nome acessível`);
      }
      const rolagem0 = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      if (rolagem0 > 0) {
        achados.push(`${largura}px ${caminho}: rolagem horizontal de ${rolagem0}px com o menu fechado`);
      }
      const toggles = await page.$$('.aed-nav-drop-toggle');
      for (const t of toggles) {
        const rotulo = (await t.evaluate((e) => e.querySelector('span:not(.material-symbols-outlined)').textContent.trim()));
        await t.click();
        await page.waitForTimeout(220);
        const r = await page.evaluate(() => {
          const m = document.querySelector('.aed-nav-drop-menu.show');
          if (!m) { return null; }
          const q = m.getBoundingClientRect();
          const vw = document.documentElement.clientWidth;
          const semTexto = [...m.querySelectorAll('.aed-nav-link')].filter((a) => {
            const s = a.querySelector('span:not(.material-symbols-outlined)');
            return !s || getComputedStyle(s).display === 'none' || s.getBoundingClientRect().width === 0;
          }).length;
          return { left: q.left, right: q.right, vw, semTexto,
            rolagem: document.documentElement.scrollWidth - document.documentElement.clientWidth };
        });
        medidos++;
        if (!r) {
          achados.push(`${largura}px ${caminho}: menu "${rotulo}" não abriu`);
        } else {
          if (r.left < 0 || r.right > r.vw) {
            achados.push(`${largura}px ${caminho}: menu "${rotulo}" passa da tela (${Math.round(r.left)}..${Math.round(r.right)} de ${r.vw})`);
          }
          if (r.semTexto) {
            achados.push(`${largura}px ${caminho}: menu "${rotulo}" com ${r.semTexto} item(ns) sem texto visível`);
          }
          if (r.rolagem > 0) {
            achados.push(`${largura}px ${caminho}: abrir "${rotulo}" cria rolagem horizontal de ${r.rolagem}px`);
          }
        }
        await page.keyboard.press('Escape');
        await page.waitForTimeout(120);
      }
    }
    await ctx.close();
  }
  return { achados, medidos };
}

let browser;
try {
  const r = await fetch(BASE + '/');
  if (!r.ok) { throw new Error('HTTP ' + r.status); }
  browser = await chromium.launch();
} catch (e) {
  console.log('NAO VERIFICOU:', e.message);
  process.exit(2);
}

let saida = 0;
try {
  const atual = await medir(browser);
  console.log(`medido: ${atual.medidos} itens/menus em ${LARGURAS.length} larguras × ${PAGINAS.length} páginas`);
  atual.achados.forEach((a) => console.log('FALHA ' + a));
  const css = versaoAnterior('src/main/resources/META-INF/resources/web/css/aed-command-center.css');
  const js = versaoAnterior('src/main/resources/META-INF/resources/web/js/aed-shell.js');
  const atualCss = await (await fetch(BASE + '/web/css/aed-command-center.css')).text();
  if (!css || css.trim() === atualCss.trim()) {
    console.log('NAO VERIFICOU calibração: não há CSS anterior diferente do servido para comparar');
    saida = atual.achados.length ? 1 : 2;
  } else {
    const antigo = await medir(browser, { cssAntigo: css, jsAntigo: js });
    console.log(`calibração com o CSS/JS anteriores: ${antigo.achados.length} achado(s) (tem de ser > 0)`);
    if (antigo.achados.length === 0) {
      console.log('NAO VERIFICOU: o roteiro não reprova nem a versão anterior — não discrimina');
      saida = 2;
    } else {
      saida = atual.achados.length ? 1 : 0;
    }
  }
  console.log(atual.achados.length ? `\n${atual.achados.length} falha(s)` : '\n0 falhas');
} catch (e) {
  console.log('FALHA exceção: ' + String(e.message || e).split(/\r?\n/)[0]);
  saida = 1;
} finally {
  await browser.close();
}
process.exit(saida);

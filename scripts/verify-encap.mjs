import { chromium } from 'playwright';

const BASE = process.argv[2] || 'http://localhost:8080';
const browser = await chromium.launch();
const page = await browser.newPage();
const errors = [];
page.on('pageerror', e => errors.push(String(e)));
page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });

await page.goto(BASE + '/trafego', { waitUntil: 'networkidle' });

// troca para a aba Encapsulamento
await page.click('.tab-trigger[data-tab="encapsulamento"]');
await page.waitForSelector('#form-encap', { state: 'visible' });

// tabs funcionam? painel visível
const encapVisible = await page.isVisible('#form-encap');

// encapsular
await page.click('#form-encap button[type="submit"]');
await page.waitForSelector('#encap-resultado:not(.d-none)', { timeout: 5000 });
const layers = await page.$$eval('.encap-layer', els => els.length);
const detalhes = await page.$$eval('.encap-detalhe', els => els.length);
const resumo = await page.textContent('.encap-resumo');
const payload = await page.textContent('.encap-payload');

// exemplo DNS
await page.click('#encap-exemplo-dns');
await page.waitForTimeout(800);
const appProtoDns = await page.textContent('.encap-detalhe .encap-detalhe-nome');

console.log('ENCAP_TAB_VISIBLE:', encapVisible);
console.log('LAYERS(nest):', layers);
console.log('DETALHES(cards):', detalhes);
console.log('RESUMO:', JSON.stringify((resumo||'').trim()));
console.log('PAYLOAD:', JSON.stringify((payload||'').trim()));
console.log('DNS_APP_LAYER:', JSON.stringify((appProtoDns||'').trim()));
console.log('PAGE_ERRORS:', JSON.stringify(errors));

await browser.close();

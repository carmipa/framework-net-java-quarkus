import { chromium } from "playwright";
const BASE = (process.argv[2] || "http://localhost:8087").replace(/\/$/, "");
const b = await chromium.launch({ headless: true });
const rotas = ["/", "/analise", "/localizacao", "/trafego", "/telemetria", "/documentacao", "/portas", "/resolucao-problemas"];
const viewports = [{ n: "mobile", w: 375, h: 800 }, { n: "tablet", w: 768, h: 1024 }, { n: "desktop", w: 1440, h: 900 }];
for (const vp of viewports) {
  const ctx = await b.newContext({ viewport: { width: vp.w, height: vp.h } });
  const p = await ctx.newPage();
  const problemas = [];
  for (const r of rotas) {
    try {
      await p.goto(`${BASE}${r}`, { waitUntil: "networkidle", timeout: 15000 });
      await p.waitForTimeout(400);
      const o = await p.evaluate(() => ({ sw: document.documentElement.scrollWidth, iw: window.innerWidth }));
      const overflow = o.sw - o.iw;
      if (overflow > 3) problemas.push(`${r}(+${overflow}px)`);
    } catch (e) { problemas.push(`${r}(ERR)`); }
  }
  console.log(`${vp.n} ${vp.w}px: ${problemas.length ? "OVERFLOW " + problemas.join(" ") : "OK (nenhum overflow horizontal)"}`);
  await ctx.close();
}
await b.close();

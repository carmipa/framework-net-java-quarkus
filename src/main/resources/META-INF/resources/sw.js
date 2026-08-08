/*
 * Service worker do Framework de Redes.
 *
 * PROPOSITO DE NEGOCIO
 *     Existe para tornar o site instalavel (o botao na barra de endereco e o
 *     atalho na area de trabalho) e para dar uma tela decente quando a conexao
 *     cai. NAO existe para acelerar o site.
 *
 * INVARIANTES DO DOMINIO
 *     - Estrategia NETWORK-FIRST para navegacao e para os estaticos do proprio
 *       site. Isto e deliberado: a aplicacao recebe deploy com frequencia, e um
 *       service worker cache-first serviria a versao anterior ate o usuario
 *       limpar o cache a mao. Cache aqui e rede de seguranca, nunca fonte
 *       primaria.
 *     - So intercepta GET de MESMA ORIGEM. Requisicao a CDN, a API externa ou
 *       com qualquer outro metodo passa direto, sem o service worker no meio.
 *     - NUNCA cacheia rota autenticada nem dado de telemetria: /telemetria,
 *       /admin, /export e /history ficam de fora, porque o cache do browser
 *       sobrevive ao logout e guardaria conteudo protegido no disco.
 *     - Cada versao usa um nome de cache proprio; caches antigos sao apagados no
 *       activate, entao o deploy nao deixa lixo acumulado.
 *
 * COMPORTAMENTO EM CASO DE FALHA
 *     Rede indisponivel devolve a copia em cache; sem copia, devolve a pagina
 *     /offline.html para navegacao e um erro 503 sintetico para o resto. Nenhuma
 *     falha do service worker impede a aplicacao de funcionar online.
 */
const VERSAO = 'framework-net-v1';
const CACHE_ESTATICO = `${VERSAO}-estatico`;
const PAGINA_OFFLINE = '/offline.html';

/* Casca minima: so o que faz a tela offline existir por si so. */
const PRE_CACHE = [
  PAGINA_OFFLINE,
  '/web/css/app.css',
  '/web/css/aed-command-center.css',
  '/pwa/icone-192.png',
];

/* Prefixos que nunca entram no cache — conteudo autenticado ou volatil. */
const NUNCA_CACHEAR = ['/telemetria', '/admin', '/export', '/history', '/health'];

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches
      .open(CACHE_ESTATICO)
      .then((cache) => cache.addAll(PRE_CACHE))
      // Falha de pre-cache nao pode impedir a instalacao: o site funciona online
      // de qualquer jeito, e sem instalar nao ha nem opcao de atalho.
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((nomes) =>
        Promise.all(
          nomes
            .filter((nome) => nome.startsWith('framework-net-') && !nome.startsWith(VERSAO))
            .map((nome) => caches.delete(nome)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

function ehProtegida(url) {
  return NUNCA_CACHEAR.some((prefixo) => url.pathname.startsWith(prefixo));
}

self.addEventListener('fetch', (evento) => {
  const req = evento.request;
  if (req.method !== 'GET') {
    return;
  }

  const url = new URL(req.url);
  if (url.origin !== self.location.origin || ehProtegida(url)) {
    return;
  }

  evento.respondWith(
    fetch(req)
      .then((resposta) => {
        // Só guarda resposta completa e bem-sucedida: cachear 206/opaque
        // devolveria pagina truncada depois, offline.
        if (resposta && resposta.status === 200 && resposta.type === 'basic') {
          const copia = resposta.clone();
          caches.open(CACHE_ESTATICO).then((cache) => cache.put(req, copia)).catch(() => undefined);
        }
        return resposta;
      })
      .catch(() =>
        caches.match(req).then((emCache) => {
          if (emCache) {
            return emCache;
          }
          if (req.mode === 'navigate') {
            return caches.match(PAGINA_OFFLINE);
          }
          return new Response('', { status: 503, statusText: 'Offline' });
        }),
      ),
  );
});

TAREFA ORIGINAL: Corrigir os 12 achados confirmados da auditoria completa do framework-net-java-quarkus.
OBJETIVO FINAL: Todos os achados fechados com artefato (teste calibrado vermelho->verde, build verde) ou declarados como risco residual com motivo.
CRITÉRIO DE ENCERRAMENTO: `gradle build` verde + cada correção com teste caso-controle + commit por onda.
BRANCH / COMMIT BASE: main @ db31b43
SHA-256 DO DOCUMENTO-REGRA: portão ABERTO nesta sessao (d249b4c6-9de9-40f0-9102-e0ae887012fb)

FILA ORDENADA DO ESCOPO:
Onda 1 (rede de seguranca do nucleo):
- [ ] 1. Teste de nao-sobreposicao VLSM (blocos LAN+WAN disjuntos + comportam hosts)
- [ ] 2. Ipv6CalculatorTest com valores concretos (compressao/expansao/prefixo)
- [ ] 3. parseIpv4Parts nos DOIS Ipv4Kernel: guarda de comprimento -> EntradaInvalidaException; split("\\.",-1); + testes
Onda 2 (robustez/hardening):
- [ ] 4. Decoder auto: desempatar por versao de IP antes do EtherType + teste IPv4 cru
- [ ] 5. trusted-proxies frageis (rede docker external) — avaliar fix robusto
- [ ] 6. Piso quarkus >= 3.37.0 no build + .env/.env.* no .dockerignore
Onda 3 (divida arquitetura):
- [ ] 7. Mover DnsResolucaoException (ou generica de rede) para shared/
- [ ] 8. Corrigir assercoes auto-anuladas + rele de PDF/ZIP nos testes
- [ ] 9. Extrair validacao de ResolucaoProblemasResource; avaliar ProtocoloAprofundamento->shared
Onda 4 (anti-XSS):
- [ ] 10. Remover <script> inline dos templates + nonce/hash no CSP (maior risco: Google Translate)

FECHADO COM ARTEFATO (gradle build = 784 testes, 0 falhas):
- 1. Teste nao-sobreposicao VLSM (VlsmServiceTest.blocosVlsmNaoSobrepoem...) — FEITO
- 2. Ipv6CalculatorTest (novo) — FEITO
- 3. parseIpv4Parts guarda comprimento + split(-1) nos DOIS kernels + testes — FEITO
- 4. Decoder resolverInicio desempata IP cru antes do EtherType + teste A1 — FEITO
- 6. Piso quarkus>=3.37.0 (build.gradle) + .env no .dockerignore — FEITO
- 8. Assercao auto-anulada corrigida (invariante real de alocacao) + rele PDF/ZIP — FEITO

DECLARADO (decisao de Paulo / refator maior — NAO defeito de comportamento):
- 5. trusted-proxies CIDR fixo: risco residual JA declarado; fix robusto toca nginx de 3 dominios (fora deste repo) = decisao de Paulo. App ja correto.
- 7. Mover DnsResolucaoException->shared: viola fatia mas SEM dano visivel; move quebra o mapeamento HTTP (AnaliseDidaticaExceptionMapper) em varios modulos. Refator com risco -> aguardando OK de Paulo.
- 8-arq/ProtocoloAprofundamento->shared: ACOPLAMENTO ACEITO documentado (ACOPLAMENTOS_ACEITOS). Decisao de arquitetura, nao bug.
- 9. Extrair validacao de ResolucaoProblemasResource (759L): refator de manutenibilidade sem defeito -> aguardando OK de Paulo.
- 10. CSP unsafe-inline: 🟡 aceito ha meses pela dependencia do Google Translate. Fechar = trocar/remover Translate = decisao de Paulo.

EM ANDAMENTO AGORA:
- Nada. Ondas 1-3 COMMITADAS em c48eb11 (2026-09-15). Reconciliado em 2026-09-24 (sessao 51d37306):
  o texto anterior dizia "aguardando decisao sobre commits", e o git mostra o contrario
  (`git log db31b43..HEAD` = 21 commits; origin/main...HEAD = 0 0). Depois disso vieram as frentes IPv6.

AUDITORIA COMPLETA 2026-09-24 (HEAD 60678fa, 865 testes verdes):
- Relatorio: C:\cerebro_de_ia\cerebro_de_ia\chats6-09-24_claude-framework-net-auditoria-completa.md
- Scratchpad da sessao 51d37306: achados.md (F01-F46) e res.py (resumo de resultados de teste)

TAREFA ATUAL (Paulo, 2026-09-24): "corrija todos uma a um". Commits locais, SEM push.
Metodo por item: teste vermelho pela causa (A2) com gabarito RFC/Python (A3) -> fix -> verde -> varrer classe.

FECHADO COM ARTEFATO:
- 8864b2c F35 F36 F37 F38 F39
- a8acc2f F01(kernel) F40 F44 F45 F46

FILA (ordem):
- math: F42 decoder TotalLength+IPv6 comprimido; F43 UDP checksum 0->FFFF; F15 split trailing dot (Encapsulamento, Acl, ConstrutorPacote); F41 ">=" (dado morto)
- disponibilidade: F02 teto localidades no import turma; F06 HistoricoStore lock+atomico+boot tolerante; F03 DnsResolver fila limitada+cancel; F04 rate limit (slug + HEAVY /ipv6/api,/localizacao/api,/history); F05 /localizacao/api/ip so literal; F29 Nominatim reverse cache+throttle
- privacidade: F07 historico por sessao (cookie HttpOnly id aleatorio, particao, teto, replay); F08 http.route sanitizado; F30 sw.js; F34 cookie __Host- em prod
- frontend: F09 informacoes .raw; F11 datagrid escape; F12 ipv6 export/copiar por aba; F13 widget mascara; F14 htmx 403/429/500; F10 mermaid click; F31 console limpar; F32 privacidade dual-stack; F17 fonte local; F18 translate=no; F19 aria-hidden; F20 SRI/pin
- testes/build/ops/docs: F16 moduloDePath+teste derivado; F22 ArquiteturaCamadasTest (+ item 7 DnsResolucaoException->shared); F23 assercoes; F24 403 dono; F25 Assumptions rede; F26 csp/sitemap/robots; F21 Dockerfile com testes; F27 logging compose+README; F28 stream re-teste; F33 README

PROXIMA ACAO EXECUTAVEL EXATA:
- F42: teste em TrafegoDecoderServiceTest com quadro TCP ACK + 6 bytes de padding -> payload 0; depois fix em TrafegoDecoderService.java:80

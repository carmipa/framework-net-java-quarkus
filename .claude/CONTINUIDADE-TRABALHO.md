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
- Ondas 1-3 (defeitos reais) fechadas e verdes. Aguardando decisao de Paulo sobre commits e sobre refatores 7 e 9.

PROXIMA ACAO EXECUTAVEL EXATA:
- Se Paulo autorizar: commit por onda (sem push). Se autorizar refator: item 7 (exceção shared + mapper) e/ou item 9.

TESTES / GUARDAS: gradle build = BUILD SUCCESSFUL, 784 testes 0 falhas. Nao ha Redis local (cache L2 off por default).
GAPS E BLOQUEIOS REAIS: nenhum.
NAO REPETIR: assumir que PlanningResult.locations() vem ordenado — ele preserva a ordem de ENTRADA (a alocacao e que e maior-primeiro).

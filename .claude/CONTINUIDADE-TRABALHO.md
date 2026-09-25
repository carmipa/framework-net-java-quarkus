TAREFA ORIGINAL (Paulo, 2026-09-24): "corrija todos uma a um" — os 46 achados (F01-F46) da auditoria completa.
OBJETIVO FINAL: todo achado fechado com artefato (teste vermelho pela causa -> verde, A1/A2/A3) ou declarado
como risco residual com motivo; revisao pos-implementacao nas tres lentes (adversarial, boa-fe, operacional).
CRITERIO DE ENCERRAMENTO: suite completa verde com --rerun-tasks + roteiro de navegador + CSP + registro no vault.
BRANCH / COMMIT BASE: main @ 60678fa (865 testes). Commits locais, SEM push (git push exige confirmacao de Paulo).
PORTAO: sessao 51d37306-73ee-436e-a9bc-1e12fdd0de47, recarimbado em 2026-09-25 apos releitura integral
(ENGENHARIA 1136, REGRA-DO-DOCKER 2402, regra 25 341, regras 21-24, tres revisoes, indice, java-quarkus-qute 893).

AUDITORIA: C:\cerebro_de_ia\cerebro_de_ia\chats\2026-09-24_claude-framework-net-auditoria-completa.md
Scratchpad da sessao: achados.md (F01-F46), res.py (resumo de resultados de teste).

FECHADO COM ARTEFATO (commits locais):
- 8864b2c F35-F39 | a8acc2f F01 F40 F44 F45 F46 | 12f3918 F15 F41 F42 F43 | 5d474d9 F02 | 57a74a0 F06
- fa326de F07 | 3b5d0a2 F03 | 8a25e53 F04 | 6ce1d54 F05 | e496db5 F29 | 647235f F08 | ae5f11a F30
- 2296a95 F34 | e2c37f2 F09 F11 F12 F13 | 8b9f08d F14 + UI-14 | a9a6ab2 F10 F31 F32
- f54de79 F17 F18 F19 | a7c0e77 F20 | 0a7ba29 fixes do navegador + scripts/verificar-auditoria-frontend.mjs (14/14)
- b694879 F16 | 3f72453 F22 | e669b87 F23 | c204508 F24 | 1eb05cb F25 | a6fa7a2 F26 | 560fba9 F28
- 284c5fb F27 | b0c9b4f F33 | 44883e7 F21 (imagem Docker so monta com a suite verde; 2 efeitos colaterais corrigidos)
Revisao pos-implementacao (lente adversarial, 7 achados):
- f26d750 #1 cookie __Host-fnet_hist em producao (fixacao de sessao)
- 116b73e #6 sw.js v3 nao cacheia /analise e /informacoes (A2: mutacao reprovou por /analise)
- ab0bbce #2 freio do Nominatim espera o slot (fallback cidade/UF voltou); A2 nos dois lados da fronteira
- bb64583 #4 rate limit por rota x global em perfis separados; 3 mutacoes, cada uma reprova so o seu teste
- #3 global 600/min atras de NAT de laboratorio, #5 heavy x matriz ';', #7 dataset aceita texto livre curto:
  RISCO RESIDUAL a declarar no relatorio (sem correcao nesta frente)

EM ANDAMENTO AGORA:
- Lentes de boa-fe e de falha operacional sobre `git diff 60678fa..HEAD` rodando em subagentes isolados.

PROXIMA ACAO EXECUTAVEL EXATA:
- Ao chegar cada lente: julgar cada achado com as tres lentes de conclusao (correcao / impacto / ja resolvido),
  corrigir os confirmados (teste A2 + commit), registrar os refutados com motivo.
- Depois: ./gradlew.bat test --rerun-tasks (suite completa); subir quarkusDev na 8089 com
  -Dframework.dev.open-browser=false; node scripts/verificar-auditoria-frontend.mjs; node scripts/verificar-csp.mjs.
- Registro no vault: chats/2026-09-25_... + 00-INDICE + projetos/framework-net-java-quarkus.md, commit no vault.

INFORMAR PAULO NO FECHAMENTO:
- PID 15668 (java de subagente) segurava build/quarkus-app e C:\deployments; nao matei.
- Docker Desktop iniciado por mim; imagem framework-net:auditoria-teste ficou local.
- Paginas do proxy (scripts/erro-proxy) regeneradas: vao a VPS pelo script proprio, nao pelo deploy.
- Nada foi empurrado.

NAO REPETIR:
- Heredoc do bash corrompe \\n e \\. em Java/JS: usar Edit/Write ou script Python em arquivo.
- `gradlew clean` falha com o PID 15668 vivo: usar `test --rerun-tasks`.
- Qute {|...|} remove as chaves externas: usar {|{ ... }|} quando o JSON precisa delas.

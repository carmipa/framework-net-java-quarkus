# Framework de Redes — Análise Didática Avançada (Java / Quarkus)

Migração do framework didático de redes de **Python/Flask** para **Java 25 + Quarkus 3.37**.

Ferramenta para análise de IPv4/IPv6, CIDR, máscaras, wildcard, VLSM, topologia WAN, scripts Cisco CLI, catálogos de portas/protocolos, GeoIP e telemetria.

![Banner](src/main/resources/META-INF/resources/icone.png)

## Módulos

| Módulo | Rota | Descrição |
|--------|------|-----------|
| Análise Didática | `/` | CIDR, máscara, wildcard, auto-IP, domínio, IPv6, comparador, GeoIP |
| Portas | `/portas` | Catálogo interativo (30 entradas) |
| Protocolos | `/protocolos` | Catálogo + troubleshooting roteamento |
| Resolução VLSM | `/resolucao-problemas` | Cenários FIAP, exports Packet Tracer, **ZIP da turma** |
| Telemetria | `/telemetria` | Dashboard de eventos e console ao vivo |
| Documentação | `/documentacao` | README técnico renderizado |

## Requisitos

- JDK **25**
- Gradle (wrapper incluído)

## Desenvolvimento

```powershell
cd D:\PROJETOS-OPEN\framework-net-java-quarkus
.\gradlew.bat quarkusDev
```

Aplicação em `http://localhost:8080`. Browser dev: `%dev.framework.dev.open-browser=true`.

## Testes

```powershell
.\gradlew.bat test
```

## Docker (VPS)

```powershell
docker compose -f docker-compose.yml up -d --build
```

Perfil `prod`: proxy reverso habilitado, dados em `/deployments/data`.

## Exportações — Resolução de Problemas

| Ação | Arquivo |
|------|---------|
| `export` | `config_packet_tracer_consolidado.txt` |
| `export_zip` | `laboratorio_packet_tracer.zip` |
| `export_entrega` | `documentacao_cenario_rede.txt` |
| `export_class_zip` | `pacote_turma_packet_tracer.zip` (`por_aluno/<aluno>/`) |

### Importar turma (Excel)

Cole na página Resolução (TAB entre colunas):

```
Nome | Rede base | Hosts1 | Hosts2
```

## Referência Python

Paridade com `FRAMEWORK_DE_REDES_ANALISE_DIDATICA_AVANCADA` (Flask). Código legado em `python/` no repositório.

## Autor

Paulo André Carminati — FIAP 2026 — Cyber Defense

Repositório: [github.com/carmipa/FRAMEWORK_DE_REDES_ANALISE_DIDATICA_AVANCADA](https://github.com/carmipa/FRAMEWORK_DE_REDES_ANALISE_DIDATICA_AVANCADA)

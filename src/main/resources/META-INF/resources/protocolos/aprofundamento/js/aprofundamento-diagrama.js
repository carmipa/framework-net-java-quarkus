/*
 * Renderiza os diagramas Mermaid das páginas de aprofundamento genéricas, no
 * tema DARK do projeto (mesma paleta da Documentação).
 *
 * Propósito de negócio: o diagrama de arquitetura/fluxo é o coração didático de
 * cada aprofundamento; sem ele desenhado, a página perde a explicação visual.
 *
 * Invariantes: securityLevel "strict" (o texto do diagrama vem de conteudo.json
 * do próprio projeto, mas nunca renderiza HTML embutido); só processa blocos
 * .aprof-mermaid, sem tocar em outros usos de Mermaid na aplicação.
 *
 * Comportamento em caso de falha: Mermaid ausente (CDN bloqueado) ou definição
 * inválida não quebram a página — o bloco cai no fallback CSS (a definição em
 * texto mono) e o erro fica só no console.
 */
(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") {
            return;
        }
        mermaid.initialize({
            startOnLoad: false,
            securityLevel: "strict",
            theme: "base",
            themeVariables: {
                darkMode: true,
                background: "#0a0e16",
                mainBkg: "#0f1622",
                primaryColor: "#0f1622",
                primaryTextColor: "#eef2f8",
                primaryBorderColor: "#2dd4bf",
                secondaryColor: "#111a27",
                tertiaryColor: "#0a0e16",
                lineColor: "#5b6679",
                textColor: "#c9d1d9",
                clusterBkg: "#0c1220",
                clusterBorder: "rgba(255,255,255,0.12)",
                nodeBorder: "rgba(255,255,255,0.18)",
                edgeLabelBackground: "#0a0e16",
                fontFamily: "Inter Tight, system-ui, sans-serif",
                fontSize: "14px"
            }
        });
        try {
            mermaid.run({ querySelector: ".aprof-mermaid" });
        } catch (e) {
            /* diagramas inválidos não devem quebrar a página */
            console.warn("Mermaid não conseguiu renderizar o diagrama", e);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initMermaid);
    } else {
        initMermaid();
    }
})();

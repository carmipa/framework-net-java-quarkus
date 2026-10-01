(function () {
    "use strict";

    /** Scroll ao topo ao trocar de rota (navegação entre telas). */
    document.querySelectorAll(".aed-nav-link").forEach((link) => {
        link.addEventListener("click", () => {
            window.scrollTo({ top: 0, behavior: "smooth" });
        });
    });

    /** Desabilita tooltips nativos vazios (regra do prompt). */
    document.querySelectorAll("[title='']").forEach((el) => el.removeAttribute("title"));

    /**
     * Menu suspenso do topo que passaria da borda da tela.
     *
     * PROPÓSITO DE NEGÓCIO: o menu abre ancorado no botão (Bootstrap com display estático); o
     *   botão do lado direito abria o menu para fora da tela — medido a 768 px: "Topologia" ia até
     *   813 px, com itens cortados que não dá para ler nem tocar.
     * INVARIANTES: só mexe na posição horizontal do menu aberto; nunca o esconde; no celular o CSS
     *   já o transforma em painel da largura do menu (left/right com !important vencem este ajuste).
     * FALHA: sem Bootstrap ou sem o menu, não faz nada.
     */
    document.addEventListener("shown.bs.dropdown", (ev) => {
        const caixa = ev.target && ev.target.closest(".aed-nav-drop");
        const menu = caixa && caixa.querySelector(".aed-nav-drop-menu");
        if (!menu) {
            return;
        }
        menu.style.left = "";
        menu.style.right = "";
        const largura = document.documentElement.clientWidth;
        let r = menu.getBoundingClientRect();
        if (r.right > largura - 8) {
            menu.style.left = "auto";
            menu.style.right = "0";
            r = menu.getBoundingClientRect();
        }
        if (r.left < 8) {
            menu.style.right = "auto";
            menu.style.left = (8 - caixa.getBoundingClientRect().left) + "px";
        }
    });
})();

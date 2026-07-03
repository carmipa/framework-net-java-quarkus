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
})();

/* Módulo Tráfego: atalhos de exemplo e limpeza do decodificador.
   A decodificação em si é feita pelo htmx (ver hx-post no formulário). */
(function () {
    "use strict";

    // Ethernet + IPv4 + TCP (SYN para porta 80) — exemplo didático.
    var EXEMPLO =
        "aabb ccdd eeff 1122 3344 5566 0800\n" +
        "4500 0028 1c46 4000 4006 b1e6 c0a8 0001 c0a8 0002\n" +
        "d431 0050 0000 0000 0000 0000 5002 7210 e577 0000";

    document.addEventListener("DOMContentLoaded", function () {
        var hex = document.getElementById("trafego-hex");
        if (!hex) return; // página sem a aba do decodificador

        var exemplo = document.getElementById("trafego-exemplo");
        var limpar = document.getElementById("trafego-limpar");

        if (exemplo) {
            exemplo.addEventListener("click", function () {
                hex.value = EXEMPLO;
                document.getElementById("trafego-camada").value = "auto";
                document.getElementById("form-trafego").requestSubmit();
            });
        }
        if (limpar) {
            limpar.addEventListener("click", function () {
                hex.value = "";
                document.getElementById("trafego-resultado").replaceChildren();
            });
        }
    });
})();

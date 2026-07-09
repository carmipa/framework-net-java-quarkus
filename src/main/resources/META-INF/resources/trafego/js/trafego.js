/* Módulo Tráfego: envia o hex dump ao servidor e renderiza as camadas decodificadas. */
(function () {
    "use strict";

    // Ethernet + IPv4 + TCP (SYN para porta 80) — exemplo didático.
    var EXEMPLO =
        "aabb ccdd eeff 1122 3344 5566 0800\n" +
        "4500 0028 1c46 4000 4006 b1e6 c0a8 0001 c0a8 0002\n" +
        "d431 0050 0000 0000 0000 0000 5002 7210 e577 0000";

    function el(tag, className, text) {
        var e = document.createElement(tag);
        if (className) e.className = className;
        if (text !== undefined && text !== null) e.textContent = text;
        return e;
    }

    function painel() {
        var p = document.getElementById("trafego-resultado");
        p.classList.remove("d-none");
        p.textContent = "";
        return p;
    }

    function mostrarErro(mensagem) {
        var p = painel();
        var card = el("div", "aed-card card-tech trafego-erro");
        card.appendChild(el("strong", null, "Não foi possível decodificar. "));
        card.appendChild(document.createTextNode(mensagem || "Verifique o hex informado."));
        p.appendChild(card);
    }

    function renderCamada(camada) {
        var card = el("div", "aed-card card-tech trafego-camada");
        var head = el("div", "trafego-camada-head");
        head.appendChild(el("h3", "trafego-camada-nome", camada.nome));
        if (camada.resumo) head.appendChild(el("span", "trafego-camada-resumo", camada.resumo));
        card.appendChild(head);

        var table = el("table", "trafego-campos");
        var tbody = el("tbody");
        (camada.campos || []).forEach(function (campo) {
            var tr = el("tr");
            tr.appendChild(el("td", "trafego-campo-nome", campo.nome));
            tr.appendChild(el("td", "trafego-campo-valor", campo.valor));
            tr.appendChild(el("td", "trafego-campo-desc", campo.descricao));
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
        card.appendChild(table);
        return card;
    }

    function render(data) {
        if (!data || data.ok === false) {
            mostrarErro(data ? data.mensagem : null);
            return;
        }
        var p = painel();
        var resumo = el("div", "aed-card card-tech loc-nota");
        resumo.appendChild(el("span", "material-symbols-outlined", "lan"));
        var info = el("div");
        info.appendChild(el("strong", null, data.totalBytes + " bytes decodificados"));
        info.appendChild(document.createTextNode(
            " · camada inicial: " + data.camadaInicial + " · " + (data.camadas || []).length + " camada(s)."));
        resumo.appendChild(info);
        p.appendChild(resumo);
        (data.camadas || []).forEach(function (camada) {
            p.appendChild(renderCamada(camada));
        });
        p.firstChild.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }

    function decodificar() {
        var hex = document.getElementById("trafego-hex").value || "";
        var camada = document.getElementById("trafego-camada").value || "auto";
        var body = new URLSearchParams();
        body.set("hex", hex);
        body.set("camada", camada);
        fetch("/trafego/api/decodificar", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
            body: body.toString()
        })
            .then(function (r) { return r.json(); })
            .then(render)
            .catch(function () { mostrarErro("Falha de rede ao decodificar."); });
    }

    document.addEventListener("DOMContentLoaded", function () {
        var form = document.getElementById("form-trafego");
        var exemplo = document.getElementById("trafego-exemplo");
        var limpar = document.getElementById("trafego-limpar");

        if (form) form.addEventListener("submit", decodificar);
        if (exemplo) {
            exemplo.addEventListener("click", function () {
                document.getElementById("trafego-hex").value = EXEMPLO;
                document.getElementById("trafego-camada").value = "auto";
                decodificar();
            });
        }
        if (limpar) {
            limpar.addEventListener("click", function () {
                document.getElementById("trafego-hex").value = "";
                var p = document.getElementById("trafego-resultado");
                p.classList.add("d-none");
                p.textContent = "";
            });
        }
    });
})();

/* Simulador de encapsulamento: monta a requisição e renderiza a pilha de camadas (App→Enlace). */
(function () {
    "use strict";

    function $(id) { return document.getElementById(id); }

    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined && text !== null) e.textContent = String(text);
        return e;
    }

    function encapsular() {
        var body = new URLSearchParams({
            mensagem: $("encap-msg").value,
            transporte: $("encap-transporte").value,
            ipOrigem: $("encap-ip-origem").value,
            ipDestino: $("encap-ip-destino").value,
            portaOrigem: $("encap-porta-origem").value,
            portaDestino: $("encap-porta-destino").value
        });
        fetch("/simuladores/api/encapsular", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
            body: body.toString()
        })
            .then(function (r) { return r.json(); })
            .then(render)
            .catch(function () { mostrarErro("Falha ao consultar o servidor."); });
    }

    function mostrarErro(msg) {
        var box = $("encap-erro");
        box.textContent = "⚠ " + msg;
        box.classList.remove("d-none");
        $("encap-resultado").classList.add("d-none");
    }

    function render(d) {
        if (!d || !d.ok) {
            mostrarErro((d && d.erro) ? d.erro : "Não foi possível encapsular.");
            return;
        }
        $("encap-erro").classList.add("d-none");
        var out = $("encap-resultado");
        out.textContent = "";
        out.classList.remove("d-none");

        // Resumo
        var app = d.camadas.find(function (c) { return c.nivel === 7; });
        var resumo = el("p", "encap-resumo");
        resumo.appendChild(el("span", null, "Mensagem de "));
        resumo.appendChild(el("strong", null, (app ? app.payloadBytes : 0) + " bytes"));
        resumo.appendChild(el("span", null, " → quadro Ethernet de "));
        resumo.appendChild(el("strong", "text-teal", d.totalBytes + " bytes"));
        resumo.appendChild(el("span", null, " (" + d.transporte + " sobre IPv4)."));
        out.appendChild(resumo);

        // Pilha aninhada (encapsulamento: Enlace por fora → App por dentro)
        out.appendChild(el("h4", "encap-secao", "Encapsulamento no emissor"));
        var nest = el("div", "encap-nest");
        var host = nest;
        d.camadas.slice().reverse().forEach(function (c) {
            var layer = el("div", "encap-layer nivel-" + c.nivel);
            var head = el("div", "encap-layer-head");
            var left = el("div", "encap-layer-id");
            left.appendChild(el("span", "encap-nivel-badge", "L" + c.nivel));
            left.appendChild(el("span", "encap-layer-name", c.protocolo));
            left.appendChild(el("span", "encap-layer-pdu", c.pdu));
            head.appendChild(left);
            var right = el("span", "encap-layer-bytes",
                (c.headerBytes > 0 ? "+" + c.headerBytes + "B header · " : "") + c.totalBytes + "B");
            head.appendChild(right);
            layer.appendChild(head);
            var lbody = el("div", "encap-layer-body");
            layer.appendChild(lbody);
            host.appendChild(layer);
            host = lbody;
        });
        // conteúdo real no miolo (camada de aplicação)
        host.appendChild(el("div", "encap-payload", "“" + (d.mensagem || "") + "”"));
        out.appendChild(nest);

        // Detalhe por camada
        out.appendChild(el("h4", "encap-secao", "Cabeçalhos, campo a campo"));
        d.camadas.forEach(function (c) {
            var card = el("div", "encap-detalhe nivel-" + c.nivel);
            var h = el("div", "encap-detalhe-head");
            h.appendChild(el("span", "encap-nivel-badge", "L" + c.nivel));
            h.appendChild(el("strong", "encap-detalhe-nome", c.nome + " · " + c.protocolo));
            h.appendChild(el("span", "encap-detalhe-pdu", c.pdu + " (" + c.totalBytes + " B)"));
            card.appendChild(h);
            if (c.resumoHex) {
                var hex = el("div", "encap-hex", c.resumoHex);
                card.appendChild(hex);
            }
            var tbl = el("table", "encap-campos");
            (c.cabecalho || []).forEach(function (campo) {
                var tr = document.createElement("tr");
                tr.appendChild(el("td", "encap-campo-nome", campo.nome));
                tr.appendChild(el("td", "encap-campo-valor", campo.valor));
                tr.appendChild(el("td", "encap-campo-desc", campo.descricao));
                tbl.appendChild(tr);
            });
            card.appendChild(tbl);
            out.appendChild(card);
        });

        // Nota de desencapsulamento
        var nota = el("p", "encap-nota");
        nota.appendChild(el("span", "material-symbols-outlined", "swap_vert"));
        nota.appendChild(el("span", null,
            " No destino, o processo se inverte: cada camada lê e remove o seu cabeçalho " +
            "(desencapsulamento) até entregar a mensagem à aplicação."));
        out.appendChild(nota);
    }

    function exemploDns() {
        $("encap-msg").value = "example.com A?";
        $("encap-transporte").value = "UDP";
        $("encap-porta-origem").value = "51000";
        $("encap-porta-destino").value = "53";
        $("encap-ip-destino").value = "8.8.8.8";
        encapsular();
    }

    function limpar() {
        $("encap-resultado").classList.add("d-none");
        $("encap-erro").classList.add("d-none");
        $("encap-resultado").textContent = "";
    }

    document.addEventListener("DOMContentLoaded", function () {
        var form = $("form-encap");
        if (!form) return; // página sem a aba de encapsulamento
        form.addEventListener("submit", encapsular);
        $("encap-exemplo-dns").addEventListener("click", exemploDns);
        $("encap-limpar").addEventListener("click", limpar);
    });
})();

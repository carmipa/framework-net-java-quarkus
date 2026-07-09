/* Módulo Localização: mapa Leaflet compartilhado + consulta de CEP (ViaCEP + OSM). */
(function (w) {
    "use strict";

    var map = null;
    var marker = null;

    function initMap() {
        var el = document.getElementById("loc-map");
        if (!el || typeof L === "undefined") {
            return;
        }
        map = L.map("loc-map", { scrollWheelZoom: false }).setView([-14.235, -51.925], 4);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 19,
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        }).addTo(map);
    }

    // Exposto para o painel GeoIP (geo-panel.js) plotar resultados no mesmo mapa.
    function setMarker(lat, lon, label) {
        if (!map || lat === null || lat === undefined || lon === null || lon === undefined) {
            return false;
        }
        var la = parseFloat(lat);
        var lo = parseFloat(lon);
        if (isNaN(la) || isNaN(lo)) {
            return false;
        }
        if (marker) {
            map.removeLayer(marker);
        }
        marker = L.marker([la, lo]).addTo(map);
        if (label) {
            marker.bindPopup(label).openPopup();
        }
        map.setView([la, lo], 13);
        return true;
    }

    function item(label, value) {
        var wrap = document.createElement("div");
        wrap.className = "loc-result-item";
        var k = document.createElement("span");
        k.className = "k";
        k.textContent = label;
        var v = document.createElement("span");
        v.className = "v";
        v.textContent = (value === null || value === undefined || value === "") ? "—" : String(value);
        wrap.appendChild(k);
        wrap.appendChild(v);
        return wrap;
    }

    function painel() {
        var el = document.getElementById("loc-resultado");
        el.classList.remove("d-none");
        el.textContent = "";
        return el;
    }

    function titulo(el, texto) {
        var h = document.createElement("h5");
        h.className = "loc-form-title";
        h.textContent = texto;
        el.appendChild(h);
    }

    function grid(el) {
        var g = document.createElement("div");
        g.className = "loc-result-grid";
        el.appendChild(g);
        return g;
    }

    function aviso(el, texto, classe) {
        var p = document.createElement("p");
        p.className = classe || "loc-alerta";
        p.textContent = texto;
        el.appendChild(p);
    }

    function renderCep(data) {
        if (!data || data.ok === false) {
            var e = painel();
            titulo(e, "CEP não localizado");
            aviso(e, (data && data.mensagem) || "Verifique o CEP informado.", "loc-alerta loc-erro");
            return;
        }
        var el = painel();
        titulo(el, "Endereço por CEP");
        var g = grid(el);
        g.appendChild(item("CEP", data.cep));
        g.appendChild(item("Logradouro", data.logradouro));
        g.appendChild(item("Bairro", data.bairro));
        g.appendChild(item("Cidade", data.cidade));
        g.appendChild(item("UF", data.uf));
        g.appendChild(item("DDD", data.ddd));
        g.appendChild(item("Coordenadas", (data.lat != null && data.lon != null)
            ? (data.lat + ", " + data.lon) : null));
        g.appendChild(item("Fonte", data.fonte));
        if (data.geocoded) {
            setMarker(data.lat, data.lon, (data.logradouro || data.cidade || "CEP " + (data.cep || "")));
        } else if (data.aviso) {
            aviso(el, data.aviso, "loc-alerta");
        }
    }

    function buscarCep() {
        var cep = (document.getElementById("loc-cep").value || "").trim();
        fetch("/localizacao/api/cep?cep=" + encodeURIComponent(cep), { headers: { Accept: "application/json" } })
            .then(function (r) { return r.json(); })
            .then(renderCep)
            .catch(function () {
                var e = painel();
                titulo(e, "Falha na consulta");
                aviso(e, "Falha de rede ao consultar o CEP.", "loc-alerta loc-erro");
            });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initMap();
        var formCep = document.getElementById("form-loc-cep");
        if (formCep) {
            formCep.addEventListener("submit", buscarCep);
        }
    });

    w.LocMap = { setMarker: setMarker };
})(window);

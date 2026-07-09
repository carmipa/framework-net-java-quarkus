/* Módulo Localização: consulta IP/CEP e plota no mapa Leaflet/OpenStreetMap. */
(function () {
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
        var h = document.createElement("h3");
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

    function mostrarErro(mensagem) {
        var el = painel();
        titulo(el, "Não foi possível localizar");
        aviso(el, mensagem || "Tente novamente.", "loc-alerta loc-erro");
    }

    function renderIp(data) {
        var el = painel();
        titulo(el, "Localização por IP (aproximada)");
        var g = grid(el);
        g.appendChild(item("IP consultado", data.ip));
        g.appendChild(item("País", data.pais));
        g.appendChild(item("Região/Estado", data.regiao));
        g.appendChild(item("Cidade", data.cidade));
        g.appendChild(item("Provedor (ISP)", data.isp));
        g.appendChild(item("Coordenadas", (data.latitude != null && data.longitude != null)
            ? (data.latitude + ", " + data.longitude) : null));
        g.appendChild(item("Risco", data.risco_nivel));
        g.appendChild(item("Fonte", data.fonte));
        aviso(el, "Precisão: cidade/provedor. O IP não identifica a residência de uma pessoa.");
        if (!setMarker(data.latitude, data.longitude, "IP " + (data.ip || "") + " · " + (data.cidade || ""))) {
            if (data.reservado) {
                aviso(el, "IP reservado/privado (RFC 1918, loopback, etc.) — sem localização pública.", "loc-alerta");
            }
        }
    }

    function renderCep(data) {
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

    function buscarJson(url, onOk) {
        fetch(url, { headers: { Accept: "application/json" } })
            .then(function (r) { return r.json(); })
            .then(function (data) {
                if (data && data.ok === false) {
                    mostrarErro(data.mensagem);
                    return;
                }
                onOk(data);
            })
            .catch(function () { mostrarErro("Falha de rede na consulta."); });
    }

    function bind() {
        var formIp = document.getElementById("form-loc-ip");
        var formCep = document.getElementById("form-loc-cep");
        var meuIp = document.getElementById("loc-meu-ip");

        if (formIp) {
            formIp.addEventListener("submit", function () {
                var ip = (document.getElementById("loc-ip").value || "").trim();
                buscarJson("/localizacao/api/ip?ip=" + encodeURIComponent(ip), renderIp);
            });
        }
        if (meuIp) {
            meuIp.addEventListener("click", function () {
                buscarJson("/localizacao/api/ip", renderIp);
            });
        }
        if (formCep) {
            formCep.addEventListener("submit", function () {
                var cep = (document.getElementById("loc-cep").value || "").trim();
                buscarJson("/localizacao/api/cep?cep=" + encodeURIComponent(cep), renderCep);
            });
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        initMap();
        bind();
    });
})();

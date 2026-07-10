/* Autoteste de vazamento de IP / privacidade — roda no navegador do próprio usuário.
 * Combina: (1) IP visto pelo servidor + cadeia de proxy (/localizacao/api/inspecao),
 * (2) IP público via ip-api (/api/informacoes/geo), (3) candidatos WebRTC/STUN,
 * (4) fingerprint do navegador. Nada é enviado a terceiros nem persistido. */
(function (w, d) {
    "use strict";

    var STUN = "stun:stun.l.google.com:19302";
    var esc = (w.HtmlEscape && w.HtmlEscape.esc) ? w.HtmlEscape.esc : function (s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    };

    function $(id) { return d.getElementById(id); }

    function ehPrivadoV4(ip) {
        return /^10\./.test(ip) || /^192\.168\./.test(ip) || /^169\.254\./.test(ip) ||
            /^127\./.test(ip) || /^172\.(1[6-9]|2\d|3[01])\./.test(ip) || /^100\.(6[4-9]|[7-9]\d|1[0-1]\d|12[0-7])\./.test(ip);
    }

    function classificarCandidato(addr, tipo) {
        if (/\.local$/i.test(addr)) return { grupo: "local", rotulo: "Rede local (protegida por mDNS)", cor: "ok" };
        if (/^fe80:/i.test(addr)) return { grupo: "local", rotulo: "IPv6 link-local", cor: "ok" };
        if (/^[0-9.]+$/.test(addr) && ehPrivadoV4(addr)) return { grupo: "privado", rotulo: "IP da rede local exposto", cor: "warn" };
        return { grupo: "publico", rotulo: "IP público (via STUN)" + (tipo ? " · " + tipo : ""), cor: "risk" };
    }

    function coletarWebRTC(timeoutMs) {
        return new Promise(function (resolve) {
            var RTC = w.RTCPeerConnection || w.webkitRTCPeerConnection || w.mozRTCPeerConnection;
            if (!RTC) { resolve({ suportado: false, candidatos: [] }); return; }
            var vistos = {};
            var lista = [];
            var pc;
            try {
                pc = new RTC({ iceServers: [{ urls: STUN }] });
            } catch (e) { resolve({ suportado: false, candidatos: [] }); return; }
            try { pc.createDataChannel("probe"); } catch (e) { /* ignore */ }

            pc.onicecandidate = function (ev) {
                if (!ev || !ev.candidate) return;
                var c = ev.candidate;
                var addr = c.address;
                var tipo = c.type;
                if (!addr && c.candidate) {
                    var parts = c.candidate.split(" ");
                    addr = parts[4];
                    var mt = c.candidate.match(/typ (\w+)/);
                    tipo = mt ? mt[1] : null;
                }
                if (!addr || addr === "0.0.0.0") return;
                var chave = addr + "|" + (tipo || "");
                if (vistos[chave]) return;
                vistos[chave] = true;
                lista.push({ addr: addr, tipo: tipo, classe: classificarCandidato(addr, tipo) });
            };

            pc.createOffer().then(function (o) { return pc.setLocalDescription(o); }).catch(function () {});
            setTimeout(function () {
                try { pc.close(); } catch (e) { /* ignore */ }
                resolve({ suportado: true, candidatos: lista });
            }, timeoutMs || 2500);
        });
    }

    function hash32(str) {
        var h = 0;
        for (var i = 0; i < str.length; i++) { h = (h << 5) - h + str.charCodeAt(i); h |= 0; }
        return (h >>> 0).toString(16);
    }

    function fingerprint() {
        var n = navigator, s = screen;
        var canvasHash = "--";
        try {
            var cv = d.createElement("canvas");
            var ctx = cv.getContext("2d");
            ctx.textBaseline = "top";
            ctx.font = "14px Arial";
            ctx.fillStyle = "#f60";
            ctx.fillRect(0, 0, 100, 20);
            ctx.fillStyle = "#069";
            ctx.fillText("framework-net ☁", 2, 2);
            canvasHash = hash32(cv.toDataURL());
        } catch (e) { /* ignore */ }
        return {
            userAgent: n.userAgent,
            plataforma: n.platform || "--",
            idiomas: (n.languages && n.languages.length ? n.languages.join(", ") : (n.language || "--")),
            timezone: (w.Intl && Intl.DateTimeFormat().resolvedOptions().timeZone) || "--",
            tela: s.width + "x" + s.height + " @" + (w.devicePixelRatio || 1) + "x",
            cores: n.hardwareConcurrency || "--",
            memoria: n.deviceMemory ? n.deviceMemory + " GB" : "--",
            toque: (("ontouchstart" in w) || n.maxTouchPoints > 0) ? "sim" : "não",
            canvasHash: canvasHash
        };
    }

    function buscarJson(url) {
        return fetch(url, { credentials: "same-origin", headers: { Accept: "application/json" } })
            .then(function (r) { return r.ok ? r.json() : null; })
            .catch(function () { return null; });
    }

    function item(k, v, mono) {
        return '<div class="loc-result-item"><div class="k">' + esc(k) + '</div>' +
            '<div class="v"' + (mono ? '' : ' style="font-family:inherit;"') + '>' + esc(v || "—") + "</div></div>";
    }

    function bandeiraHtml(cc, emoji) {
        var iso = String(cc || "").replace(/\s+/g, "").toUpperCase();
        if (/^[A-Z]{2}$/.test(iso)) {
            return '<img src="https://flagcdn.com/w80/' + iso.toLowerCase() + '.png" width="46" height="34" class="priv-flag" alt="' + esc(iso) + '" loading="lazy">';
        }
        return '<span class="priv-flag-emoji">' + esc(emoji || "🌐") + "</span>";
    }

    function secaoLocalizacao(geo) {
        var head = '<h6 class="priv-sec"><span class="material-symbols-outlined">flag</span> Sua localização (pelo IP)</h6>';
        var temGeo = geo && geo.ok && geo.reservado !== true && (geo.pais || geo.pais_codigo || geo.codigo_pais);
        if (!temGeo) {
            return head + '<p class="small text-secondary mb-0">Indisponível para este IP (reservado / rede local, ou a consulta externa falhou). ' +
                'Em produção, atrás do proxy, aqui aparece o país real.</p>';
        }
        var cc = geo.pais_codigo || geo.codigo_pais || "";
        var coords = (geo.latitude != null && geo.longitude != null) ? (geo.latitude + ", " + geo.longitude) : "—";
        return head +
            '<div class="priv-local">' +
            '<div class="priv-local-flag">' + bandeiraHtml(cc, geo.pais_bandeira) +
            '<div><div class="priv-local-pais">' + esc(geo.pais || "—") + "</div>" +
            (cc ? '<div class="priv-local-cc">' + esc(cc) + "</div>" : "") + "</div></div>" +
            '<div class="loc-result-grid priv-local-grid">' +
            item("Estado / Região", geo.regiao) +
            item("Cidade", geo.cidade) +
            item("Fuso horário", geo.timezone) +
            item("Coordenadas", coords, true) +
            item("ISP (Provedor)", geo.isp) +
            item("IP público", geo.ip, true) +
            "</div></div>";
    }

    function render(dados) {
        var root = $("priv-resultado");
        if (!root) return;
        var insp = dados.inspecao || {};
        var geo = dados.geo || {};
        var rtc = dados.webrtc || { suportado: false, candidatos: [] };
        var fp = dados.fingerprint || {};

        var ipServidor = insp.ipReal || insp.ipConexao || "—";
        var ipPublico = geo.ip || "—";
        var proxyFlag = geo.proxy === true;
        var publicos = rtc.candidatos.filter(function (c) { return c.classe.grupo === "publico"; });
        var privados = rtc.candidatos.filter(function (c) { return c.classe.grupo === "privado"; });
        var ipWebrtcPublico = publicos.length ? publicos[0].addr : null;

        // veredito
        var alertas = [];
        if (proxyFlag) alertas.push("O provedor sinaliza <strong>Proxy/VPN</strong> para o IP público.");
        if (ipWebrtcPublico && ipServidor && ipWebrtcPublico !== ipServidor && ipServidor !== "127.0.0.1") {
            alertas.push("<strong>Divergência de IP público</strong>: o servidor vê <code>" + esc(ipServidor) +
                "</code> e o WebRTC revela <code>" + esc(ipWebrtcPublico) + "</code> — típico de VPN/proxy com vazamento WebRTC.");
        }
        if (privados.length) alertas.push("Seu <strong>IP de rede local</strong> foi exposto via WebRTC (" +
            esc(privados.map(function (c) { return c.addr; }).join(", ")) + ").");
        if (insp.atrasDeProxy) alertas.push("A requisição chegou <strong>através de proxy</strong> (cabeçalhos de encaminhamento presentes).");

        var nivel, texto;
        if (alertas.length >= 2) { nivel = "risk"; texto = "Exposição relevante detectada"; }
        else if (alertas.length === 1) { nivel = "warn"; texto = "Atenção — um ponto de exposição"; }
        else { nivel = "ok"; texto = "Sem vazamentos óbvios neste navegador"; }

        var html = '<div class="priv-verdict priv-' + nivel + '">' +
            '<span class="material-symbols-outlined">' + (nivel === "ok" ? "verified_user" : nivel === "warn" ? "shield" : "gpp_bad") + '</span>' +
            '<div><strong>' + esc(texto) + '</strong>' +
            (alertas.length ? '<ul class="priv-alertas">' + alertas.map(function (a) { return "<li>" + a + "</li>"; }).join("") + "</ul>"
                : '<div class="small text-secondary">O que dava pra medir não revelou divergência de IP nem IP local aberto.</div>') +
            "</div></div>";

        // Localização pelo IP (bandeira + país) — repetido de propósito p/ documentar aqui também
        html += secaoLocalizacao(geo);

        // IP público (as visões)
        html += '<h6 class="priv-sec"><span class="material-symbols-outlined">public</span> Seu IP público (três visões)</h6>' +
            '<div class="loc-result-grid">' +
            item("Servidor vê (IP real)", ipServidor, true) +
            item("ip-api vê", ipPublico, true) +
            item("WebRTC (STUN) revela", ipWebrtcPublico || "não revelou", true) +
            item("Proxy/VPN pelo provedor", proxyFlag ? "sim" : "não") +
            "</div>";

        // WebRTC
        html += '<h6 class="priv-sec"><span class="material-symbols-outlined">lan</span> Candidatos WebRTC</h6>';
        if (!rtc.suportado) {
            html += '<p class="small text-secondary mb-0">WebRTC não suportado ou bloqueado neste navegador (bom para privacidade).</p>';
        } else if (!rtc.candidatos.length) {
            html += '<p class="small text-secondary mb-0">Nenhum candidato exposto — navegador protegendo os IPs (mDNS/hardening).</p>';
        } else {
            html += '<div class="priv-cands">' + rtc.candidatos.map(function (c) {
                return '<div class="priv-cand priv-' + c.classe.cor + '"><code>' + esc(c.addr) + '</code>' +
                    '<span>' + esc(c.classe.rotulo) + "</span></div>";
            }).join("") + "</div>";
        }

        // Cabeçalhos de proxy
        html += '<h6 class="priv-sec"><span class="material-symbols-outlined">dns</span> Cadeia de cabeçalhos (como o servidor resolveu o IP)</h6>' +
            '<div class="loc-result-grid">' +
            item("IP da conexão", insp.ipConexao, true) +
            item("X-Forwarded-For", insp.xForwardedFor, true) +
            item("X-Real-IP", insp.xRealIp, true) +
            item("Via", insp.via, true) +
            item("CF-Connecting-IP", insp.cfConnectingIp, true) +
            item("Accept-Language", insp.acceptLanguage) +
            "</div>";

        // Fingerprint
        html += '<h6 class="priv-sec"><span class="material-symbols-outlined">fingerprint</span> Fingerprint do navegador (identifica mesmo sem o IP)</h6>' +
            '<div class="loc-result-grid">' +
            item("Fuso horário", fp.timezone) +
            item("Idiomas", fp.idiomas) +
            item("Tela", fp.tela, true) +
            item("Núcleos de CPU", fp.cores) +
            item("Memória", fp.memoria) +
            item("Toque", fp.toque) +
            item("Plataforma", fp.plataforma) +
            item("Canvas hash", fp.canvasHash, true) +
            item("User-Agent", fp.userAgent) +
            "</div>";

        root.innerHTML = html;
        root.classList.remove("d-none");
    }

    function rodar() {
        var btn = $("btn-priv-testar");
        var status = $("priv-status");
        if (btn) { btn.disabled = true; }
        if (status) { status.textContent = "Coletando (WebRTC leva ~2,5s)..."; status.classList.remove("d-none"); }

        Promise.all([
            buscarJson("/localizacao/api/inspecao"),
            buscarJson("/api/informacoes/geo"),
            coletarWebRTC(2500)
        ]).then(function (res) {
            render({ inspecao: res[0], geo: res[1], webrtc: res[2], fingerprint: fingerprint() });
        }).catch(function (e) {
            var root = $("priv-resultado");
            if (root) { root.classList.remove("d-none"); root.innerHTML = '<div class="alert alert-secondary mb-0">Falha ao rodar o teste: ' + esc(e) + "</div>"; }
        }).finally(function () {
            if (btn) { btn.disabled = false; }
            if (status) { status.classList.add("d-none"); }
        });
    }

    d.addEventListener("DOMContentLoaded", function () {
        var btn = $("btn-priv-testar");
        if (btn) btn.addEventListener("click", rodar);
    });
})(window, document);

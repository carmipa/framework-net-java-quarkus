/* Globo 3D (Globe.gl + Three.js). Fábrica que cria globos independentes:
 *  - #geo-globe  → aba GeoIP (controlado por geo-panel.js via window.GeoGlobe)
 *  - #priv-globe → aba Vazamento/Privacidade (controlado por privacidade.js via window.PrivGlobe)
 * Cada globo plota o ponto do IP com marcador + anel. Texturas via CDN (three-globe).
 * Se o WebGL falhar, chama opts.onFail. */
import * as THREE from "three";
import Globe from "globe.gl";

(function () {
    "use strict";

    var EARTH_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-blue-marble.jpg";
    var BUMP_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-topology.png";
    var CLOUDS_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/clouds/clouds.png";
    var CLOUDS_ALT = 0.01;
    var CLOUDS_SPEED = -0.006;

    function criarGlobo(el, opts) {
        opts = opts || {};
        var globe = null;
        var clouds = null;
        var ready = false;
        var pendente = null;

        function apply(loc) {
            if (!ready || !globe || !loc) return;
            var lat = Number(loc.lat);
            var lon = Number(loc.lon);
            if (!isFinite(lat) || !isFinite(lon)) return;
            var data = [{ lat: lat, lng: lon, label: loc.label || "" }];
            globe.pointsData(data).ringsData(data);
            globe.pointOfView({ lat: lat, lng: lon, altitude: 1.5 }, 1500);
        }

        function build() {
            if (!el || globe) return;
            try {
                globe = Globe()(el)
                    .backgroundColor("rgba(0,0,0,0)")
                    .globeImageUrl(EARTH_IMG)
                    .bumpImageUrl(BUMP_IMG)
                    .showAtmosphere(true)
                    .atmosphereColor("#4da6ff")
                    .atmosphereAltitude(0.18)
                    .pointsData([])
                    .pointLat("lat")
                    .pointLng("lng")
                    .pointColor(function () { return "#ff3131"; })
                    .pointAltitude(0.08)
                    .pointRadius(0.5)
                    .pointLabel(function (p) { return "<strong>" + (p.label || "") + "</strong>"; })
                    .ringsData([])
                    .ringLat("lat")
                    .ringLng("lng")
                    .ringColor(function () { return function (t) { return "rgba(255,49,49," + (1 - t) + ")"; }; })
                    .ringMaxRadius(4)
                    .ringPropagationSpeed(2)
                    .ringRepeatPeriod(900);
            } catch (e) {
                if (opts.onFail) opts.onFail();
                return;
            }

            var ctrl = globe.controls();
            ctrl.autoRotate = true;
            ctrl.autoRotateSpeed = 0.5;
            ctrl.enableDamping = true;

            new THREE.TextureLoader().load(CLOUDS_IMG, function (tex) {
                clouds = new THREE.Mesh(
                    new THREE.SphereGeometry(globe.getGlobeRadius() * (1 + CLOUDS_ALT), 75, 75),
                    new THREE.MeshPhongMaterial({ map: tex, transparent: true, opacity: 0.55, depthWrite: false })
                );
                globe.scene().add(clouds);
                (function spin() {
                    if (clouds) clouds.rotation.y += CLOUDS_SPEED * Math.PI / 180;
                    requestAnimationFrame(spin);
                })();
            }, undefined, function () { /* nuvens são opcionais */ });

            function resize() {
                var w = el.clientWidth || 420;
                var h = el.clientHeight || 420;
                globe.width(w).height(h);
            }
            resize();
            if (window.ResizeObserver) new ResizeObserver(resize).observe(el);
            else window.addEventListener("resize", resize);

            ready = true;
            globe.pointOfView({ lat: -14, lng: -52, altitude: 1.9 }, 0); // Brasil, enquanto não há ponto
            if (pendente) apply(pendente);
        }

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", build);
        } else {
            build();
        }

        return {
            setLocation: function (lat, lon, label) {
                pendente = { lat: lat, lon: lon, label: label };
                apply(pendente);
            }
        };
    }

    // Globo da aba GeoIP — mantém o contrato usado por geo-panel.js (window.__geoPendingLoc).
    var geo = criarGlobo(document.getElementById("geo-globe"), {
        onFail: function () {
            var wrap = document.querySelector(".geo-globe-wrap");
            if (wrap) wrap.remove();
            var box = document.getElementById("geo-sucesso");
            if (box) box.classList.remove("geo-split");
        }
    });
    window.GeoGlobe = {
        setLocation: function (lat, lon, label) {
            window.__geoPendingLoc = { lat: lat, lon: lon, label: label };
            geo.setLocation(lat, lon, label);
        }
    };
    if (window.__geoPendingLoc) {
        geo.setLocation(window.__geoPendingLoc.lat, window.__geoPendingLoc.lon, window.__geoPendingLoc.label);
    }

    // Globo da aba Vazamento/Privacidade (opcional — só se o container existir).
    var privEl = document.getElementById("priv-globe");
    if (privEl) {
        var priv = criarGlobo(privEl, {
            onFail: function () {
                var wrap = document.getElementById("priv-globe-wrap");
                if (wrap) wrap.classList.add("d-none");
            }
        });
        window.PrivGlobe = {
            setLocation: function (lat, lon, label) { priv.setLocation(lat, lon, label); }
        };
    }
})();

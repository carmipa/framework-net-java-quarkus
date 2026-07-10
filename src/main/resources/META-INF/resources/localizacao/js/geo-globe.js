/* Globo 3D (Globe.gl + Three.js) ao lado do relatório GeoIP.
 * Renderiza um planeta interativo com atmosfera e nuvens e plota o ponto do IP
 * localizado. O painel (geo-panel.js) chama window.GeoGlobe.setLocation(lat, lon, label).
 * Texturas via CDN (three-globe). Se o WebGL falhar, o card volta a uma coluna. */
import * as THREE from "three";
import Globe from "globe.gl";

(function () {
    "use strict";

    var EARTH_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-blue-marble.jpg";
    var BUMP_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/img/earth-topology.png";
    var CLOUDS_IMG = "https://cdn.jsdelivr.net/npm/three-globe/example/clouds/clouds.png";
    var CLOUDS_ALT = 0.01;
    var CLOUDS_SPEED = -0.006;

    var globe = null;
    var clouds = null;
    var ready = false;

    function collapse() {
        var wrap = document.querySelector(".geo-globe-wrap");
        if (wrap) wrap.remove();
        var box = document.getElementById("geo-sucesso");
        if (box) box.classList.remove("geo-split");
    }

    function build() {
        var el = document.getElementById("geo-globe");
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
            collapse();
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
        if (window.__geoPendingLoc) apply(window.__geoPendingLoc);
    }

    function apply(loc) {
        if (!ready || !globe || !loc) return;
        var lat = Number(loc.lat);
        var lon = Number(loc.lon);
        if (!isFinite(lat) || !isFinite(lon)) return;
        var data = [{ lat: lat, lng: lon, label: loc.label || "" }];
        globe.pointsData(data).ringsData(data);
        globe.pointOfView({ lat: lat, lng: lon, altitude: 1.5 }, 1500);
    }

    window.GeoGlobe = {
        setLocation: function (lat, lon, label) {
            var loc = { lat: lat, lon: lon, label: label };
            window.__geoPendingLoc = loc;
            apply(loc);
        }
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", build);
    } else {
        build();
    }
})();

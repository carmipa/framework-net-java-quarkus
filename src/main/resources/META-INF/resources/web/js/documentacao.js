(function () {
    "use strict";

    function initMermaid() {
        if (typeof mermaid === "undefined") return;
        mermaid.initialize({ startOnLoad: true, securityLevel: "strict", theme: "dark" });
        mermaid.run({ querySelector: ".markdown-doc .mermaid" });
    }

    function initScrollSpy() {
        const links = Array.from(document.querySelectorAll(".doc-sidebar a[href^='#']"));
        const sections = links
            .map((link) => document.querySelector(link.getAttribute("href")))
            .filter(Boolean);
        if (!links.length || !sections.length) return;

        const sync = () => {
            const threshold = window.scrollY + 140;
            let activeId = sections[0].id;
            sections.forEach((sec) => {
                if (sec.offsetTop <= threshold) activeId = sec.id;
            });
            links.forEach((link) => {
                link.classList.toggle("active", link.getAttribute("href") === "#" + activeId);
            });
        };

        links.forEach((link) => {
            link.addEventListener("click", () => {
                links.forEach((l) => l.classList.remove("active"));
                link.classList.add("active");
            });
        });
        window.addEventListener("scroll", sync, { passive: true });
        sync();
    }

    function boot() {
        initMermaid();
        initScrollSpy();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }
})();

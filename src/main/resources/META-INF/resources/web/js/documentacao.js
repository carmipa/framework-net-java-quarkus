/* Documentação: scroll-spy do sumário agrupado (sem Mermaid — diagramas são HTML/CSS dark). */
(function () {
    "use strict";

    function initScrollSpy() {
        const links = Array.from(document.querySelectorAll(".doc-nav a[href^='#']"));
        const sections = links
            .map((link) => document.querySelector(link.getAttribute("href")))
            .filter(Boolean);
        if (!links.length || !sections.length) return;

        const sync = () => {
            const threshold = window.scrollY + 160;
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

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", initScrollSpy);
    } else {
        initScrollSpy();
    }
})();

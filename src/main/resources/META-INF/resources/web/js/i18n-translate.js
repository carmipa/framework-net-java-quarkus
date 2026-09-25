// Internacionalização via Google Translate (client-side).
// IMPORTANTE: este arquivo é servido estaticamente e NÃO passa pelo parser do Qute,
// por isso os objetos literais { ... } do JS são seguros aqui (dentro de um template
// Qute, `{pageLanguage:...}` seria interpretado como expressão e quebraria a página).

// Callback global chamado por translate_a/element.js?cb=googleTranslateElementInit
function googleTranslateElementInit() {
    new google.translate.TranslateElement(
        { pageLanguage: 'pt', autoDisplay: false },
        'google_translate_element'
    );
}

// Troca de idioma acionada pelos botões de bandeira (main_menu.html)
function aedTranslate(lang) {
    var domain = window.location.hostname;
    if (lang === 'pt') {
        document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
        document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=' + domain + ';';
    } else {
        document.cookie = 'googtrans=/pt/' + lang + '; path=/;';
        document.cookie = 'googtrans=/pt/' + lang + '; path=/; domain=' + domain + ';';
    }
    window.location.reload();
}

// Ilhas técnicas não se traduzem (regra de i18n do projeto, §4): nome de ícone Material Symbols
// traduzido APAGA o glifo ("delete" → "borrar"), e IP/CLI/hex traduzidos viram lixo. Os templates já
// nascem carimbados; isto carimba o que o JavaScript pinta DEPOIS do carregamento (resultados htmx,
// tabelas dinâmicas), que de outra forma nasceria desprotegido.
(function () {
    var SELETOR = '.material-symbols-outlined, code, pre, kbd, samp';

    function carimbar(el) {
        if (el.getAttribute('translate') !== 'no') {
            el.setAttribute('translate', 'no');
        }
        if (!el.classList.contains('notranslate')) {
            el.classList.add('notranslate');
        }
        if (el.classList.contains('material-symbols-outlined') && !el.hasAttribute('aria-hidden')
                && !el.hasAttribute('aria-label') && !el.hasAttribute('role')) {
            el.setAttribute('aria-hidden', 'true');
        }
    }

    function varrer(raiz) {
        if (!raiz || raiz.nodeType !== 1) {
            return;
        }
        if (raiz.matches && raiz.matches(SELETOR)) {
            carimbar(raiz);
        }
        raiz.querySelectorAll(SELETOR).forEach(carimbar);
    }

    function iniciar() {
        varrer(document.body);
        new MutationObserver(function (mutacoes) {
            mutacoes.forEach(function (m) {
                m.addedNodes.forEach(varrer);
            });
        }).observe(document.body, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})();

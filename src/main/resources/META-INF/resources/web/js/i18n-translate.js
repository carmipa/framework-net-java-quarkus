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

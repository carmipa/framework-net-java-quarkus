(function (w) {
    "use strict";

    // Todo POST/PUT/DELETE disparado pelo htmx leva o token CSRF no cabeçalho,
    // do mesmo jeito que o csrf-client.js já faz com o fetch nativo.
    document.body.addEventListener("htmx:configRequest", (event) => {
        const token = w.CsrfClient ? w.CsrfClient.getToken() : "";
        if (token) {
            event.detail.headers["X-CSRF-Token"] = token;
        }
    });

    // Por padrão o htmx descarta respostas de erro. Os mapeadores de exceção
    // devolvem o fragmento de erro já renderizado quando a requisição vem do
    // htmx, então o 400 também deve ser trocado no alvo.
    document.body.addEventListener("htmx:beforeSwap", (event) => {
        if (event.detail.xhr && event.detail.xhr.status === 400) {
            event.detail.shouldSwap = true;
            event.detail.isError = false;
        }
    });
})(window);

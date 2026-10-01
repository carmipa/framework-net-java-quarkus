/**
 * Pinta todos os selos de progresso da página ([data-acad-selo="<licaoId>"]).
 *
 * PROPÓSITO DE NEGÓCIO: a landing e as páginas de nível mostram, lição por lição, o que já foi
 *   feito neste navegador — sem o servidor escrever nada do aluno no HTML (R5).
 *
 * INVARIANTES DO DOMÍNIO: lê só o AcademiaProgresso; não grava nada.
 *
 * COMPORTAMENTO EM CASO DE FALHA: sem o AcademiaProgresso carregado, os selos ficam como vieram no
 *   HTML e nada é lançado.
 */
(function () {
    'use strict';
    if (!window.AcademiaProgresso) {
        return;
    }
    Array.prototype.forEach.call(document.querySelectorAll('[data-acad-selo]'), function (selo) {
        window.AcademiaProgresso.pintarSelo(selo, selo.getAttribute('data-acad-selo'));
    });
}());

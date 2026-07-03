package org.framework.net.analiseDidatica.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSimplesServiceTest {

    @Test
    void deveGerarPdfBasico() throws Exception {
        byte[] pdf = PdfSimplesService.gerarPdfSimples("Linha 1\nLinha 2");
        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 8).startsWith("%PDF-1.4"));
    }
}

package org.framework.net.resolucaoProblemas;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.framework.net.resolucaoProblemas.application.importing.BulkClassImportService;
import org.framework.net.resolucaoProblemas.domain.model.ClassRosterRow;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class BulkClassImportServiceTest {

    @Inject
    BulkClassImportService bulkClassImportService;

    @Test
    void parseClassRosterPasteTabSeparated() {
        String paste = """
                Nome\tRede\tHosts1\tHosts2
                João Silva\t172.51.0.0\t400\t390
                Maria Souza\t172.52.0.0/16\t350\t300
                """;
        List<ClassRosterRow> rows = bulkClassImportService.parseClassRosterPaste(paste);
        assertEquals(2, rows.size());
        assertEquals("João Silva", rows.get(0).getStudentName());
        assertEquals("172.51.0.0/16", rows.get(0).getBaseNetwork());
        assertEquals(2, rows.get(0).getLocations().size());
        assertEquals("400", rows.get(0).getLocations().get(0).getHosts());
        assertTrue(rows.get(0).getFolderSlug() != null && !rows.get(0).getFolderSlug().isBlank());
    }

    @Test
    void parseClassRosterPasteVazioFalha() {
        assertThrows(EntradaInvalidaException.class, () -> bulkClassImportService.parseClassRosterPaste("  "));
    }

    @Test
    void parseClassRosterPasteColunasInsuficientes() {
        assertThrows(EntradaInvalidaException.class, () ->
                bulkClassImportService.parseClassRosterPaste("Aluno\t172.51.0.0\t400"));
    }
}

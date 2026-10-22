package com.recycllux.pelanode.web;

import com.recycllux.pelanode.domain.ContextRecord;
import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.service.ContextImportService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Step B-17: POST /context/import, e.g. the organiser's hydrology data during the hackathon. */
@RestController
public class ContextImportController {

    private final ContextImportService imports;

    public ContextImportController(ContextImportService imports) {
        this.imports = imports;
    }

    public record ImportRequest(DatasetSource dataset, List<ContextRecord> records, String missionId) {
    }

    @PostMapping("/api/v1/context/import")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importContext(@RequestBody ImportRequest req) {
        if (req == null) {
            throw ApiException.invalid("body is required");
        }
        return imports.importDataset(req.dataset(), req.records(), req.missionId());
    }
}

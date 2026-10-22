package com.recycllux.pelanode.web;

import com.recycllux.pelanode.service.ContextImportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ContextImportController {

    private final ContextImportService imports;

    @PostMapping("/api/v1/context/import")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> importContext(@RequestBody(required = false) ContextImportService.Request req) {
        return imports.importDataset(req);
    }
}

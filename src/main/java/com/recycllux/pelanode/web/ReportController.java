package com.recycllux.pelanode.web;

import com.recycllux.pelanode.service.ReportService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions/{id}")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    public record ExportRequest(String format) {
    }

    @GetMapping("/report")
    public Map<String, Object> summary(@PathVariable String id) {
        return reports.summary(id);
    }

    @PostMapping("/export")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> export(@PathVariable String id, @RequestBody ExportRequest req) {
        if (req == null || req.format() == null) {
            throw ApiException.invalid("format is required");
        }
        return reports.export(id, req.format());
    }
}

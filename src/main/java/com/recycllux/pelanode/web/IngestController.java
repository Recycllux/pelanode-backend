package com.recycllux.pelanode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.service.IngestResult;
import com.recycllux.pelanode.service.IngestService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingest;

    @PostMapping("/trackpoints")
    public Map<String, Object> trackpoints(@RequestBody JsonNode body) {
        return ingest.trackPoints(body);
    }

    @PostMapping("/observations")
    public ResponseEntity<Observation> observation(@Valid @RequestBody Observation o) {
        return status(ingest.observation(o), o);
    }

    @PostMapping("/nodes/{id}/status")
    public ResponseEntity<DeviceStatus> status(@PathVariable String id, @Valid @RequestBody DeviceStatus s) {
        return status(ingest.deviceStatus(id, s), s);
    }

    @PostMapping("/model-outputs")
    public Map<String, Object> modelOutputs(@RequestBody JsonNode body) {
        return ingest.modelOutputs(body);
    }

    private static <T> ResponseEntity<T> status(IngestResult result, T body) {
        return ResponseEntity.status(result == IngestResult.CREATED ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }
}

package com.recycllux.pelanode.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.ModelOutput;
import com.recycllux.pelanode.domain.Observation;
import com.recycllux.pelanode.domain.TrackPoint;
import com.recycllux.pelanode.service.IngestResult;
import com.recycllux.pelanode.service.IngestService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Step B-06: write endpoints for node data (P0-13 section 8). */
@RestController
@RequestMapping("/api/v1")
public class IngestController {

    private static final int MAX_BATCH = 1000;

    private final IngestService ingest;
    private final ObjectMapper mapper;
    private final Validator validator;

    public IngestController(IngestService ingest, ObjectMapper mapper, Validator validator) {
        this.ingest = ingest;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** One track point or an array of up to 1,000. */
    @PostMapping("/trackpoints")
    public Map<String, Object> trackpoints(@RequestBody JsonNode body) {
        int accepted = 0;
        int duplicates = 0;
        List<Map<String, Object>> rejected = new ArrayList<>();
        for (JsonNode item : items(body)) {
            String id = item.path("track_point_id").asText(null);
            try {
                TrackPoint p = convert(item, TrackPoint.class);
                if (ingest.trackPoint(p) == IngestResult.CREATED) {
                    accepted++;
                } else {
                    duplicates++;
                }
            } catch (ApiException e) {
                rejected.add(Map.of("track_point_id", String.valueOf(id), "reason", e.getMessage()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("duplicates", duplicates);
        out.put("rejected", rejected);
        return out;
    }

    @PostMapping("/observations")
    public ResponseEntity<Observation> observation(@Valid @RequestBody Observation o) {
        IngestResult r = ingest.observation(o);
        return ResponseEntity.status(r == IngestResult.CREATED ? HttpStatus.CREATED : HttpStatus.OK).body(o);
    }

    @PostMapping("/nodes/{id}/status")
    public ResponseEntity<DeviceStatus> status(@PathVariable String id, @Valid @RequestBody DeviceStatus s) {
        if (!id.equals(s.nodeId())) {
            throw ApiException.invalid("node_id in body does not match the path");
        }
        IngestResult r = ingest.deviceStatus(s);
        return ResponseEntity.status(r == IngestResult.CREATED ? HttpStatus.CREATED : HttpStatus.OK).body(s);
    }

    /** One model output or an array; each one runs the event builder. */
    @PostMapping("/model-outputs")
    public Map<String, Object> modelOutputs(@RequestBody JsonNode body) {
        int accepted = 0;
        int duplicates = 0;
        List<String> created = new ArrayList<>();
        for (JsonNode item : items(body)) {
            IngestService.ModelOutputResult r = ingest.modelOutput(convert(item, ModelOutput.class));
            if (r.result() == IngestResult.CREATED) {
                accepted++;
            } else {
                duplicates++;
            }
            r.eventCreated().ifPresent(created::add);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("duplicates", duplicates);
        out.put("events_created", created);
        return out;
    }

    private List<JsonNode> items(JsonNode body) {
        List<JsonNode> list = new ArrayList<>();
        if (body.isArray()) {
            body.forEach(list::add);
        } else {
            list.add(body);
        }
        if (list.size() > MAX_BATCH) {
            throw ApiException.invalid("At most " + MAX_BATCH + " items per request");
        }
        return list;
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        T value;
        try {
            value = mapper.treeToValue(node, type);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            throw ApiException.invalid(root.getMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw ApiException.invalid(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted().collect(Collectors.joining("; ")));
        }
        return value;
    }
}

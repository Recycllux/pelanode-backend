package com.recycllux.pelanode.web;

import com.recycllux.pelanode.domain.DatasetSource;
import com.recycllux.pelanode.domain.DeviceStatus;
import com.recycllux.pelanode.domain.Mission;
import com.recycllux.pelanode.service.MissionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missions;

    @GetMapping("/missions")
    public List<Mission> list() {
        return missions.list();
    }

    @GetMapping("/missions/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return missions.get(id);
    }

    @GetMapping("/missions/{id}/context")
    public Map<String, Object> context(@PathVariable String id,
                                       @RequestParam(required = false) String variable,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return missions.context(id, variable, from, to);
    }

    @GetMapping("/datasets")
    public List<DatasetSource> datasets() {
        return missions.datasets();
    }

    @GetMapping("/nodes/{id}/status")
    public DeviceStatus status(@PathVariable String id, @RequestParam("mission_id") String missionId) {
        return missions.nodeStatus(id, missionId);
    }
}

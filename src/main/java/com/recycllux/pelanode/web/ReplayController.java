package com.recycllux.pelanode.web;

import com.recycllux.pelanode.service.ReplayService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/missions/{id}/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replay;

    @GetMapping
    public Map<String, Object> get(@PathVariable String id) {
        return replay.view(id);
    }

    @PostMapping
    public Map<String, Object> control(@PathVariable String id, @RequestBody ReplayService.Command cmd) {
        return replay.control(id, cmd);
    }
}

package com.recycllux.pelanode.web;

import com.recycllux.pelanode.service.DemoService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demo;

    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody(required = false) DemoService.ResetRequest req) {
        return demo.reset(req);
    }
}

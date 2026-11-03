package io.github.controlleraudit.itest;

import io.github.controlleraudit.annotation.AuditIgnore;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class HelloController {

    @PostMapping("/hello")
    public Map<String, String> hello(@RequestBody Map<String, String> body) {
        return Map.of("greeting", "hi " + body.get("name"));
    }

    @AuditIgnore
    @GetMapping("/quiet")
    public String quiet() {
        return "shh";
    }
}

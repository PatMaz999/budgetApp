package pk.ni.pasir_mazurek_patryk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public String test() {
        return "Hello world";
    }

    @GetMapping("api/info")
    public Map<String, String> apiInfo() {
        return Map.of(
                "appName", "Aplikacja Budżetowa",
                "version", "1.0",
                "message", "Witaj w aplikacji budżetowej stworzonej ze Spring Boot!"
        );
    }

}

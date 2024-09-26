package com.example.smartchargebackend.controller;

import com.example.smartchargebackend.records.HelloWorld;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {

    private static final String template = "Hello, %s!";

    @GetMapping("/greeting")
    public HelloWorld greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return new HelloWorld(String.format(template, name));
    }
}
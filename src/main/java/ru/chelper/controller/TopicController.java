package ru.chelper.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.chelper.dto.TopicDto;
import ru.chelper.service.TopicService;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public List<TopicDto> list() {
        return topicService.findAll();
    }

    @GetMapping("/search")
    public List<TopicDto> search(@RequestParam(required = false) String q) {
        return topicService.search(q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopicDto> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(topicService.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

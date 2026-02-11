package ru.chelper.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.chelper.dto.TopicDto;
import ru.chelper.entity.Topic;
import ru.chelper.repository.TopicRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TopicService {

    private final TopicRepository topicRepository;

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicDto> findAll() {
        return topicRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TopicDto> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return findAll();
        }
        return topicRepository.searchByTitleOrDescription(query.trim()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TopicDto getById(Long id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Тема не найдена"));
        return toDtoFull(topic);
    }

    @Transactional
    public TopicDto create(TopicDto dto) {
        Topic topic = new Topic();
        topic.setTitle(dto.getTitle());
        topic.setDescription(dto.getDescription());
        topic.setContent(dto.getContent());
        topic.setImagePath(dto.getImagePath());
        topic.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        topic = topicRepository.save(topic);
        return toDto(topic);
    }

    @Transactional
    public TopicDto update(Long id, TopicDto dto) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Тема не найдена"));
        if (dto.getTitle() != null) topic.setTitle(dto.getTitle());
        if (dto.getDescription() != null) topic.setDescription(dto.getDescription());
        if (dto.getContent() != null) topic.setContent(dto.getContent());
        if (dto.getImagePath() != null) topic.setImagePath(dto.getImagePath());
        if (dto.getSortOrder() != null) topic.setSortOrder(dto.getSortOrder());
        topic = topicRepository.save(topic);
        return toDtoFull(topic);
    }

    @Transactional
    public void delete(Long id) {
        if (!topicRepository.existsById(id)) {
            throw new IllegalArgumentException("Тема не найдена");
        }
        topicRepository.deleteById(id);
    }

    private TopicDto toDto(Topic t) {
        TopicDto dto = new TopicDto();
        dto.setId(t.getId());
        dto.setTitle(t.getTitle());
        dto.setDescription(t.getDescription());
        dto.setSortOrder(t.getSortOrder());
        dto.setHasTasks(t.getTasks() != null && !t.getTasks().isEmpty());
        return dto;
    }

    private TopicDto toDtoFull(Topic t) {
        TopicDto dto = toDto(t);
        dto.setContent(t.getContent());
        dto.setImagePath(t.getImagePath());
        return dto;
    }
}

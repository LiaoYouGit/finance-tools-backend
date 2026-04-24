package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.dao.mapper.SuggestionMapper;
import com.finance.model.entity.Suggestion;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SuggestionService {

    private final SuggestionMapper suggestionMapper;

    public SuggestionService(SuggestionMapper suggestionMapper) {
        this.suggestionMapper = suggestionMapper;
    }

    public void submit(Long userId, String account, String content) {
        Suggestion suggestion = new Suggestion();
        suggestion.setUserId(userId);
        suggestion.setAccount(account);
        suggestion.setContent(content);
        suggestion.setCreateTime(LocalDateTime.now());
        suggestionMapper.insert(suggestion);
    }

    public List<Suggestion> list() {
        return suggestionMapper.selectList(
                new LambdaQueryWrapper<Suggestion>().orderByDesc(Suggestion::getCreateTime));
    }
}

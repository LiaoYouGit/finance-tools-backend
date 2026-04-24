package com.finance.web;

import com.finance.common.Result;
import com.finance.model.entity.Suggestion;
import com.finance.service.SuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/suggestion")
public class SuggestionController {

    @Autowired
    private SuggestionService suggestionService;

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, String> body, Authentication authentication) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.fail(400, "意见内容不能为空");
        }
        if (content.trim().length() < 5) {
            return Result.fail(400, "意见内容不能少于5个字");
        }
        Long userId = (Long) authentication.getDetails();
        String account = (String) authentication.getPrincipal();
        suggestionService.submit(userId, account, content.trim());
        return Result.ok(null);
    }

    @GetMapping("/list")
    public Result<List<Suggestion>> list() {
        return Result.ok(suggestionService.list());
    }
}

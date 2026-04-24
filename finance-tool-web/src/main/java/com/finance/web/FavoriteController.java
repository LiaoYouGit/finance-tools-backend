package com.finance.web;

import com.finance.common.Result;
import com.finance.model.entity.Favorite;
import com.finance.service.FavoriteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorite")
public class FavoriteController {

    @Autowired
    private FavoriteService favoriteService;

    @PostMapping("/toggle")
    public Result<Boolean> toggle(@RequestBody Map<String, Object> body, Authentication authentication) {
        String toolKey = (String) body.get("toolKey");
        Object toolIdObj = body.get("toolId");
        if (toolKey == null || toolKey.trim().isEmpty()) {
            return Result.fail(400, "toolKey不能为空");
        }
        Long toolId = toolIdObj != null ? Long.valueOf(toolIdObj.toString()) : null;
        Long userId = (Long) authentication.getDetails();
        boolean starred = favoriteService.toggle(userId, toolKey.trim(), toolId);
        return Result.ok(starred);
    }

    @PostMapping("/list")
    public Result<List<Favorite>> list(Authentication authentication) {
        Long userId = (Long) authentication.getDetails();
        return Result.ok(favoriteService.list(userId));
    }

    @PostMapping("/keys")
    public Result<List<Long>> keys(Authentication authentication) {
        Long userId = (Long) authentication.getDetails();
        return Result.ok(favoriteService.getFavoriteKeys(userId));
    }
}

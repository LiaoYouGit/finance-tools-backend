package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.dao.mapper.FavoriteMapper;
import com.finance.model.entity.Favorite;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public boolean toggle(Long userId, String toolKey, Long toolId) {
        Favorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .eq(Favorite::getToolKey, toolKey));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        } else {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setToolKey(toolKey);
            favorite.setToolId(toolId);
            favorite.setCreateTime(LocalDateTime.now());
            favoriteMapper.insert(favorite);
            return true;
        }
    }

    public List<Favorite> list(Long userId) {
        return favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreateTime));
    }

    public List<Long> getFavoriteKeys(Long userId) {
        List<Favorite> list = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .select(Favorite::getToolId));
        return list.stream().map(Favorite::getToolId).filter(id -> id != null).collect(Collectors.toList());
    }
}

package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.dao.mapper.ToolMapper;
import com.finance.model.entity.Tool;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ToolService {

    private final ToolMapper toolMapper;

    public ToolService(ToolMapper toolMapper) {
        this.toolMapper = toolMapper;
    }

    public List<Tool> list() {
        return toolMapper.selectList(
                new LambdaQueryWrapper<Tool>().orderByAsc(Tool::getSortOrder));
    }
}

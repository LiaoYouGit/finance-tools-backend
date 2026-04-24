package com.finance.web;

import com.finance.common.Result;
import com.finance.model.entity.Tool;
import com.finance.service.ToolService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tool")
public class ToolController {

    @Autowired
    private ToolService toolService;

    @PostMapping("/list")
    public Result<List<Tool>> list() {
        return Result.ok(toolService.list());
    }
}

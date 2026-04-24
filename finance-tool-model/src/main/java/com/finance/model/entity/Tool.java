package com.finance.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tool")
public class Tool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String path;

    private String icon;

    private Integer sortOrder;

    private LocalDateTime createTime;
}

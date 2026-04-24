package com.finance.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("suggestion")
public class Suggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String account;

    private String content;

    private LocalDateTime createTime;
}

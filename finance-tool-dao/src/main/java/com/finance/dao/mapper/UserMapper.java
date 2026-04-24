package com.finance.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finance.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}

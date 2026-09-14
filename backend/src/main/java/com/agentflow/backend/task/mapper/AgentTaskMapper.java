package com.agentflow.backend.task.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.backend.task.model.AgentTask;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}

package com.agentflow.backend.step.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.agentflow.backend.step.model.AgentStep;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStep> {
}

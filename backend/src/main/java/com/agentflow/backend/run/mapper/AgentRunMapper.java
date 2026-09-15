package com.agentflow.backend.run.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.backend.run.model.AgentRun;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {
}

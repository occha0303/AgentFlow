package com.agentflow.backend.run.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.backend.run.model.AgentRun;

@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {
	@Select("SELECT id FROM agent_run WHERE id = #{runId} FOR UPDATE")
	Long lockRunRow(@Param("runId") Long runId);
}

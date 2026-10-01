package com.agentflow.backend.approval.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.agentflow.backend.approval.model.AgentApproval;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AgentApprovalMapper extends BaseMapper<AgentApproval> {
}

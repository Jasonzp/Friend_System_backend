package com.yupi.usercenter.service;

import com.sun.org.apache.xpath.internal.operations.Bool;
import com.yupi.usercenter.model.domain.Team;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.usercenter.model.domain.User;
import com.yupi.usercenter.model.dto.TeamQuery;
import com.yupi.usercenter.model.request.TeamAddRequest;
import com.yupi.usercenter.model.request.TeamJoinRequest;
import com.yupi.usercenter.model.request.TeamQuitRequest;
import com.yupi.usercenter.model.request.TeamUpdateRequest;
import com.yupi.usercenter.model.vo.TeamUserVO;

import java.util.List;

/**
* @author lenovo
* @description 针对表【team(队伍)】的数据库操作Service
* @createDate 2024-10-24 15:01:08
*/
public interface TeamService extends IService<Team> {


    long addTeam(Team team, User loginUser);

    List<TeamUserVO> listTeams(TeamQuery teamQuery, Boolean isAdmin);

    boolean deleteTeam(long id, User loginUser);

    boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser);

    boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser);

    boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser);

//    List<TeamUserVO> listMyTeam(TeamQuery teamQuery, User loginUser);
}

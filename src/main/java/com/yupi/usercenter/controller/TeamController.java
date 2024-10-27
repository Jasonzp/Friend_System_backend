package com.yupi.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.usercenter.common.BaseResponse;
import com.yupi.usercenter.common.DeleteRequest;
import com.yupi.usercenter.common.ErrorCode;
import com.yupi.usercenter.common.ResultUtils;
import com.yupi.usercenter.exception.BusinessException;
import com.yupi.usercenter.model.domain.Team;
import com.yupi.usercenter.model.domain.User;
import com.yupi.usercenter.model.domain.UserTeam;
import com.yupi.usercenter.model.dto.TeamQuery;
import com.yupi.usercenter.model.request.TeamAddRequest;
import com.yupi.usercenter.model.request.TeamJoinRequest;
import com.yupi.usercenter.model.request.TeamQuitRequest;
import com.yupi.usercenter.model.request.TeamUpdateRequest;
import com.yupi.usercenter.model.vo.TeamUserVO;
import com.yupi.usercenter.service.TeamService;
import com.yupi.usercenter.service.UserService;
import com.yupi.usercenter.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户接口
 *
 * @author <a href="https://github.com/Jasonzp">Jasonzp</a>
 * https://github.com/Jasonzp
 */
@Slf4j
@RestController
@RequestMapping("/team")
@CrossOrigin(origins = "http://localhost:3000")
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建队伍
     *
     * @param teamAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {


        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
//       boolean save = teamService.save(team);
        /**
         * 1. 获得当前登录用户
         * 2. 直接从请求头中获得相应字段
         * 3. 传入相关参数
         */

        User loginUser = userService.getLoginUser(request);
        long teamId = teamService.addTeam(team, loginUser);

        return ResultUtils.success(teamId);


    }


    @PostMapping("/delete")
//    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(id, loginUser);

        // 还要删除所有的
        return ResultUtils.success(result);


    }

    // todo
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null || teamUpdateRequest.getId() == null || teamUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);

        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未能完成更新");
        }


        return ResultUtils.success(true);

    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(@RequestParam("id") long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Team team = teamService.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        return ResultUtils.success(team);

    }


    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }


        boolean isAdmin = userService.isAdmin(request);
        // 判断是否是管理员

        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);

       // 查询当前用户是否已经加入队伍
        List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();

        try {

            // 获得当前用户
            User loginUser = userService.getLoginUser(request);
            userTeamQueryWrapper.eq("userId", loginUser.getId());
            userTeamQueryWrapper.in("teamId", teamIdList);
            List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
            // 已加入的队伍的id集合
            Set<Long> hasJointTeamIdSet = userTeamList.stream().map(UserTeam::getTeamId).collect(Collectors.toSet());
            teamList.forEach(team ->{
                // 是否已经加入，已经加入就返回true，未加入就返回false
                boolean hasJoin = hasJointTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);

            });
        }catch (Exception e) {}
        // 查询加入队伍的用户信息（人数）
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);

        // 队伍 id => 加入这个队伍的用户列表
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream().collect(Collectors.groupingBy(UserTeam::getTeamId));
        teamList.forEach(team -> {
           team.setHasJoinNum(teamIdUserTeamList.getOrDefault(team.getId(),new ArrayList<>()).size());

        });



        return ResultUtils.success(teamList);

    }


    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQuery teamQuery) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Team team = new Team();
        // 复制TeamQuery 到 Team 中去
        BeanUtils.copyProperties(teamQuery, team);
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());

        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> resultPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(resultPage);
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {

            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);

        return ResultUtils.success(result);
    }


    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);

        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);

    }

    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {

        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        teamQuery.setUserId(loginUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }

    /**
     * 获取我加入的队伍
     * TODO 理解下面这段代码
     * @param teamQuery
     * @param request
     * @return
     */
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);
        // 取出不重复的队伍 id
        // teamId userId
        // 1, 2
        // 1, 3
        // 2, 3
        // result
        // 1 => 2, 3
        // 2 => 3
        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }



}

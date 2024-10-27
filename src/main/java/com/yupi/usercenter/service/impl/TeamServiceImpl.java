package com.yupi.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.usercenter.common.ErrorCode;
import com.yupi.usercenter.exception.BusinessException;
import com.yupi.usercenter.mapper.TeamMapper;
import com.yupi.usercenter.model.domain.Team;
import com.yupi.usercenter.model.domain.User;
import com.yupi.usercenter.model.domain.UserTeam;
import com.yupi.usercenter.model.dto.TeamQuery;
import com.yupi.usercenter.model.enums.TeamStatusEnum;
import com.yupi.usercenter.model.request.TeamJoinRequest;
import com.yupi.usercenter.model.request.TeamQuitRequest;
import com.yupi.usercenter.model.request.TeamUpdateRequest;
import com.yupi.usercenter.model.vo.TeamUserVO;
import com.yupi.usercenter.model.vo.UserVO;
import com.yupi.usercenter.service.TeamService;
import com.yupi.usercenter.service.UserService;
import com.yupi.usercenter.service.UserTeamService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author lenovo
 * @description 针对表【team(队伍)】的数据库操作Service实现
 * @createDate 2024-10-24 15:01:08
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    /**
     * 循环引入，必然报错
     */
//    @Resource
//    TeamService teamService;

    @Resource
    UserService userService;

    // 注入userTeamService 操作连接表
    @Resource
    private UserTeamService userTeamService;

    // 加锁，加分布式锁
    @Resource
    private RedissonClient redissonClient;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addTeam(Team team, User loginUser) {

        // 1.  请求参数是否为空
        if (team == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR);
        }
        // 2. 是否登录
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }


        // 获得登录用户的id
        // 设置成final 对象，保证其不会再后面改变
        final long userId = loginUser.getId();

        // 3. 校验信息
        //  1.队伍人数 》 1 并且 《= 20
        Integer maxNum = team.getMaxNum();
        if (!(maxNum >= 1 && maxNum <= 20) || maxNum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不符合标准");
        }
        // 2.队伍标题 《= 20
        String name = team.getName();
        if (StringUtils.isBlank(name) || name.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不满足要求");
        }
        // 3. 队伍描述 《= 512
        String description = team.getDescription();
        if (StringUtils.isNotBlank(description) && description.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍人数不满足要求");
        }

        // 4.status 是否公开（int）不是默认为0（公开）
        int status = Optional.ofNullable(team.getStatus()).orElse(0);
        TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);

        if (statusEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍状态不满足要求");
        }

        // 5.如果 status 是加密状态，一定要有密码，并且密码 <= 32
        String password = team.getPassword();
        if (TeamStatusEnum.SECRET.equals(statusEnum) && (StringUtils.isBlank(password) || password.length() > 32)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码不正确");
        }

        // 6. 超时时间 > 当前时间
        Date expireTime = team.getExpireTime();
        // 允许用户不设置超时时间，如果不设置自动转化为3天后的日期
        if (expireTime == null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date());
            calendar.add(Calendar.DATE, 3);
            expireTime = calendar.getTime();
            team.setExpireTime(expireTime);
//            expireTime = new Date()
        }
        if (expireTime.before(new Date())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "过期时间有问题");

        }

        // 7. 校验用户最多创建了5个队伍
        // todo 用户疯狂点击可能同时创建100个队伍，加入到表中
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        List<Team> teamList = list(queryWrapper);
        if (teamList.size() >= 5) {
            // 限制用户最多只能创建5支队伍
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前用户已经创建的队伍数量大于5个");
        }

        // 4. 插入队伍信息到队伍表
        team.setId(null);
        // 设置当前队伍的创建人
        team.setUserId(userId);
        /**
         * 插入这只该插入的队伍
         */
        boolean result = this.save(team);
        if (!result) {
            // 插入到队伍表中失败，事务就会自动回滚
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "插入到队伍表失败");
        }

        UserTeam userTeam = new UserTeam();
//        BeanUtils.copyProperties(team,userTeam);
        // 设置创建队伍的用户/加入队伍的用户 和 队伍的关系
        userTeam.setUserId(userId);

        userTeam.setTeamId(team.getId());
        // 设置当前的加入时间
        userTeam.setJoinTime(new Date());
        // 插入用户到关系表
        result = userTeamService.save(userTeam);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "插入到队伍用户关系表失败");

        }


        return team.getId();

//        return 0;
    }

    @Override
    public List<TeamUserVO> listTeams(TeamQuery teamQuery, Boolean isAdmin) {

//        boolean isAdmin = userService.isAdmin(loginUser);
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>();

        // 如果teamQuery 为 空时，直接全部查询
        if (teamQuery != null) {
            Long id = teamQuery.getId();
            String name = teamQuery.getName();
            String description = teamQuery.getDescription();
            Integer maxNum = teamQuery.getMaxNum();
            Long userId = teamQuery.getUserId();
            Integer status = teamQuery.getStatus();
            String searchText = teamQuery.getSearchText();

            List<Long> idList = teamQuery.getIdList();
            if (!CollectionUtils.isEmpty(idList)) {
                queryWrapper.in("id", idList);
            }
            if (StringUtils.isNotBlank(searchText)) {
                // Lamda 表达式
                queryWrapper.and(qw -> qw.like("name", searchText).or().like("description", searchText));
            }
            if (id != null && id > 0) {
                queryWrapper.eq("id", id);
            }
            if (StringUtils.isNotBlank(name)) {
                // 允许模糊匹配
                queryWrapper.like("name", name);
            }

            if (StringUtils.isNotBlank(description) && description.length() < 512) {
                queryWrapper.like("description", description);
            }
            if (maxNum != null && maxNum > 0) {
                queryWrapper.eq("maxNum", maxNum);
            }

            if (userId != null && userId > 0) {
                queryWrapper.eq("userId", userId);
            }

            TeamStatusEnum statusEnum = TeamStatusEnum.getEnumByValue(status);

            if (statusEnum == null) {
                statusEnum = TeamStatusEnum.PUBLIC;
            }

            if (!isAdmin && statusEnum.equals(TeamStatusEnum.PRIVATE)) {
                throw new BusinessException(ErrorCode.NO_AUTH, "不是管理员，无法查看加密数据");
            }


            queryWrapper.eq("status", statusEnum.getValue());


        }

        // 查询队伍
        // todo 跳过已经过期的队伍
        queryWrapper.and(qw -> qw.gt("expireTime", new Date()).or().isNull("expireTime"));


        List<Team> teamList = this.list(queryWrapper);

        if (CollectionUtils.isEmpty(teamList)) {
            return Collections.emptyList();
        }

        List<TeamUserVO> teamUserVOList = new ArrayList<>();
        for (Team team : teamList) {
            // 如果当前用户我已经加入了，那么将不会再在前端展示
//            QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
//            queryWrapper.eq("userId", loginUser.getId());
//            userTeamQueryWrapper.eq("teamId", team.getId());
//            UserTeam userTeam = userTeamService.getOne(userTeamQueryWrapper);
//            if(userTeam != null) {
//                // 说明我已经加入了，那么就不展示
//                continue;
//            }

            // 拿到创建人的信息
            Long userId = team.getUserId();
            if (userId == null) {
                continue;
            }

            User user = userService.getById(userId);

            // 数据脱敏
//            User safetyUser = userService.getSafetyUser(user);
            TeamUserVO teamUserVO = new TeamUserVO();
            BeanUtils.copyProperties(team, teamUserVO);
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                teamUserVO.setCreateUser(userVO);
            }

            teamUserVOList.add(teamUserVO);
        }


        return teamUserVOList;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTeam(long id, User loginUser) {
        // id  表示的想要删除的id
        Team team = this.getById(id);
        if (team == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "想删除的队伍不在数据库中");
        }

        boolean isAdmin = userService.isAdmin(loginUser);
        // 如果自己不是管理员，但是又不是删除自己的队伍，抛异常
        if (!isAdmin && loginUser.getId() != id) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        userTeamQueryWrapper.eq("teamId", id);
        // 删除关联表
        boolean removeUserTeam = userTeamService.remove(userTeamQueryWrapper);

        boolean removeTeam = this.removeById(id);
        if (!(removeUserTeam && removeTeam)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");

        }
        return removeUserTeam && removeTeam;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateTeam(TeamUpdateRequest teamUpdateRequest, User loginUser) {

        Long oldTeamId = teamUpdateRequest.getId();

        Team oldTeam = this.getById(oldTeamId);
        // 想要修改的team不再数据库内部
        if (oldTeam == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "想要修改的队伍不在数据库中");
        }

        Integer status = teamUpdateRequest.getStatus();
        Integer updateStatus = status;
        String password = teamUpdateRequest.getPassword();
        String updatePassword = password;
        if (updateStatus != null && updateStatus == TeamStatusEnum.SECRET.getValue() && updatePassword == null) {
            // 如果当前想更改队伍的状态为加密，必须配置有密码
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前想更改队伍的状态为加密，必须配置有密码");
        }

//        if(updateStatus != null && updateStatus == TeamStatusEnum.SECRET.getValue() && updatePassword != null)


        // 当前的用户不是管理员,又想修改不是自己创建的队伍
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isAdmin && loginUser.getId() != oldTeam.getUserId()) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }


        // team
//        private Long id;
//        private String name;
//        private String description;
//        private Integer maxNum;
//        private Date expireTime;
//        private Long userId;
//        private Integer status;
//        private String password;


//        Team team = new Team();
//        BeanUtils.copyProperties(teamUpdateRequest, team);
        String name = teamUpdateRequest.getName();
        String description = teamUpdateRequest.getDescription();
        Date expireTime = teamUpdateRequest.getExpireTime();
        if (StringUtils.isNotBlank(name)) {
            oldTeam.setName(name);
        }

        if (StringUtils.isNotBlank(description)) {
            oldTeam.setDescription(description);
        }

        if (expireTime != null) {
            oldTeam.setExpireTime(expireTime);
        }

        if (status != null && status < 0 && status > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        if (status != null) {
            oldTeam.setStatus(status);
        }

        if (updatePassword != null) {
            oldTeam.setPassword(updatePassword);
        }

        Team team = new Team();
        BeanUtils.copyProperties(oldTeam, team);
// TODO 按理说，MyBatis Plus 底层应该会自动更新 UpdateTime 这个字段，但是这里没有
        team.setUpdateTime(new Date());

        boolean result = this.updateById(team);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");

        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean joinTeam(TeamJoinRequest teamJoinRequest, User loginUser) {
        Long teamId = teamJoinRequest.getTeamId();
        String password = teamJoinRequest.getPassword();
        final long userId = loginUser.getId();
        // 1. 判断你现在想加入的队伍是否已经删除
        Team team = this.getById(teamId);
        if (team == null || team.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "想加入的队伍已被删除");
        }

        // 判断队伍是否过期
        if (new Date().after(team.getExpireTime())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "队伍已经过期");
        }

        // 禁止加入个人的队伍
        if (team.getStatus() == TeamStatusEnum.PRIVATE.getValue()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "禁止加入个人队伍");
        }

        // 如果是加密队伍，必须密码正确
        if (password != null && team.getStatus() == TeamStatusEnum.SECRET.getValue() && !password.equals(team.getPassword())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码输入错误");
        }

        // 禁止加入自己创建的队伍
        if (team.getUserId() == userId) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能加入自己创建的队伍");
        }

//       RedissonClient redissonClient;

        RLock lock = redissonClient.getLock("yupao:join_team");
        try {
            // 抢到锁并执行
            while (true) {
                if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {

                    QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("userId", loginUser.getId());
                    List<UserTeam> userTeamListByUserId = userTeamService.list(userTeamQueryWrapper);
                    if (userTeamListByUserId.size() >= 5) {
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前用户加入队伍已达上线");
                    }

                    // 2. 查出所有的user_team 的项目
                    userTeamQueryWrapper = new QueryWrapper<>();
                    userTeamQueryWrapper.eq("teamId", teamId);
                    List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                    if (userTeamList.size() >= 5) {
                        // 队伍成员已经超过五个
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "队伍已满");
                    }

                    // 3. 这个队伍已经加入了
                    for (UserTeam userTeam : userTeamList) {
                        if (userTeam.getUserId().equals(userId)) {
                            throw new BusinessException(ErrorCode.PARAMS_ERROR, "这个队伍您已经加入");
                        }
                    }

                    // 最后加入到队伍中
                    UserTeam userTeam = new UserTeam();
                    BeanUtils.copyProperties(teamJoinRequest, userTeam);

                    userTeam.setIsDelete(0);
                    userTeam.setJoinTime(new Date());
                    userTeam.setUserId(userId);

                    boolean result = userTeamService.save(userTeam);
                    return result;


                }
            }
        }catch (Exception e)
        {
            log.error("doCacheRecommendUser error", e);
            return false;

        }finally {

            // 只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                System.out.println("unLock: " + Thread.currentThread().getId());
                lock.unlock();
            }

        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitTeam(TeamQuitRequest teamQuitRequest, User loginUser) {
        // 1. 获得当前的
        final Long userId = loginUser.getId();
        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
        Long teamId = teamQuitRequest.getTeamId();
        userTeamQueryWrapper.eq("teamId", teamId);
        userTeamQueryWrapper.last("order by id asc");

        // 查找是否曾经加入到这个队伍中
        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
        if (userTeamList.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "你没有加入这个队伍");
        }

        // 如果当前只剩下一名队员
        if (userTeamList.size() == 1) {
            // 当前的队伍只剩下一名队友，直接解散
            this.removeById(userTeamList.get(0).getTeamId());
            userTeamService.removeById(userTeamList.get(0).getId());
            return true;

        }

        // 如果当前的用户是队长，他退出了，那么将Team 表中的 userId 给到下一个人
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        teamQueryWrapper.eq("id", teamId);
        List<Team> teamList = this.list(teamQueryWrapper);
        if (teamList.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "当前队伍不存在");
        }
        Team team = teamList.get(0);
        // 如果当前的用户是队长，他退出了，那么直接解散这个队伍
        if (team.getUserId().equals(userId)) {

            UserTeam nextUserTeam = userTeamList.get(1);

            team.setUserId(nextUserTeam.getUserId());
            // 更新队伍
            this.updateById(team);
            // 删除自己
            userTeamService.remove(new QueryWrapper<UserTeam>().eq("userId", userId));
            // 直接返回
            return true;
        }

        // 如果当前想要退出的用户不是队伍的创建人,并且有至少大于等于两个人在队伍中
        for (UserTeam userTeam : userTeamList) {

            if (userTeam.getUserId().equals(userId)) {

                userTeamService.removeById(userTeam);
                return true;
            }
        }

        return true;
    }

//    @Override
//    public List<TeamUserVO> listMyTeam(TeamQuery teamQuery, User loginUser) {
//
//
//
//
//        // 首先查出我加入的队伍
//        QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
//        userTeamQueryWrapper.eq("userId", loginUser.getId());
//        List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
//        // 构造返回参数
//        List<TeamUserVO> teamUserVOList = new ArrayList<>();
//        for (UserTeam userTeam : userTeamList) {
//
//            if(userTeam.getIsDelete() == 1)
//            {
//                continue;
//            }
//            // 获得我加入的队伍的完整个体
//            Team team = this.getById(userTeam.getUserId());
//            TeamUserVO teamUserVO = new TeamUserVO();
//            BeanUtils.copyProperties(team, teamUserVO);
//
//            userService.
//            // 设置UserVO userVO
//            teamUserVO.setCreateUser();
//            teamUserVOList.add(teamUserVO);
//
//        }
//
//
//
//        return Collections.emptyList();
//    }
}





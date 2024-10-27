package com.yupi.usercenter.model.request;

// 本项目_所属 [Jasonzp](https://github.com/Jasonzp)

import lombok.Data;

import java.io.Serializable;

/**
 * 用户加入队伍请求体
 *
 * @author <a href="https://github.com/Jasonzp">Jasonzp</a>
 * https://github.com/Jasonzp
 */
@Data
public class TeamJoinRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * id
     */
    private Long teamId;

    /**
     * 密码
     */
    private String password;
}

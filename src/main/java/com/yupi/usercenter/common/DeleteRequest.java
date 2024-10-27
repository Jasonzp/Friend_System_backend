package com.yupi.usercenter.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用删除请求
 *
 * @author <a href="https://github.com/Jasonzp">Jasonzp</a>
 * https://github.com/Jasonzp
 */
@Data
public class DeleteRequest implements Serializable {

    private static final long serialVersionUID = -5860707094194210842L;

    private long id;
}

package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meta_ftp_config")
public class MetaFtpConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String ftpIp;

    private Integer ftpPort;

    private String username;

    private String password;

    private String remotePath;

    private String createUser;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** FTP 用户名（列名 ftp_user，JSON 键 ftpUser） */
    @TableField("ftp_user")
    private String ftpUser;

    /** FTP 密码（列名 ftp_pass，JSON 键 ftpPass）：存 GCM 密文；仅接受入参，任何响应不序列化回传 */
    @TableField("ftp_pass")
    @JsonProperty(value = "ftpPass", access = JsonProperty.Access.WRITE_ONLY)
    private String ftpPass;

    private String remotePath;

    private String createUser;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

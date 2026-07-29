package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaFtpConfig;

import java.util.List;

public interface MetaFtpConfigService extends IService<MetaFtpConfig> {

    List<MetaFtpConfig> listAll();
}

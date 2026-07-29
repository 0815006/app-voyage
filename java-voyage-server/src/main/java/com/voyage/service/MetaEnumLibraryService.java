package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaEnumLibrary;

import java.util.List;

public interface MetaEnumLibraryService extends IService<MetaEnumLibrary> {

    List<MetaEnumLibrary> listAll();

    List<String> getAllKeys();
}

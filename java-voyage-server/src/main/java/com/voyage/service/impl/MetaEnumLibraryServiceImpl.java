package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.MetaEnumLibrary;
import com.voyage.mapper.MetaEnumLibraryMapper;
import com.voyage.service.MetaEnumLibraryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MetaEnumLibraryServiceImpl
        extends ServiceImpl<MetaEnumLibraryMapper, MetaEnumLibrary>
        implements MetaEnumLibraryService {

    @Override
    public List<MetaEnumLibrary> listAll() {
        return lambdaQuery().orderByAsc(MetaEnumLibrary::getEnumKey).list();
    }

    @Override
    public List<String> getAllKeys() {
        List<MetaEnumLibrary> list = listAll();
        List<String> keys = new ArrayList<>();
        for (MetaEnumLibrary e : list) {
            keys.add(e.getEnumKey());
        }
        return keys;
    }
}

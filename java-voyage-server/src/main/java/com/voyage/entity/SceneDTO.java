package com.voyage.entity;

import lombok.Data;

import java.util.List;

/**
 * 场景及其明细的传输对象
 */
@Data
public class SceneDTO {
    private PerfTaskScene scene;
    private List<PerfTaskSceneDetail> details;
}

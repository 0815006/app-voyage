package com.voyage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 部署方案环境资源清单表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("performance_resource_info")
public class PerformanceResourceInfo {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("serial_number")
    private Integer serialNumber;

    @TableField("task_name")
    private String taskName;

    @TableField("task_num")
    private String taskNum;

    @TableField("service_name")
    private String serviceName;

    @TableField("english_short_name")
    private String englishShortName;

    @TableField("batch_name")
    private String batchName;

    @TableField("business_dept")
    private String businessDept;

    @TableField("project_type")
    private String projectType;

    @TableField("disaster_backup_level")
    private String disasterBackupLevel;

    @TableField("availability_level")
    private String availabilityLevel;

    @TableField("deployment_location")
    private String deploymentLocation;

    @TableField("network_deployment")
    private String networkDeployment;

    @TableField("system_platform")
    private String systemPlatform;

    @TableField("paas_platform_type")
    private String paasPlatformType;

    @TableField("theme_count")
    private Integer themeCount;

    @TableField("queue_count")
    private Integer queueCount;

    @TableField("shard_count")
    private Integer shardCount;

    @TableField("per_shard_capacity_gb")
    private Integer perShardCapacityGb;

    @TableField("redundancy_method")
    private String redundancyMethod;

    @TableField("operating_system")
    private String operatingSystem;

    @TableField("middleware")
    private String middleware;

    @TableField("partition_usage")
    private String partitionUsage;

    @TableField("partition_usage_name")
    private String partitionUsageName;

    @TableField("hostname")
    private String hostname;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("backup_ip")
    private String backupIp;

    @TableField("cpu_cores")
    private Integer cpuCores;

    @TableField("memory_gb")
    private Integer memoryGb;

    @TableField("dedicated_storage_gb")
    private Integer dedicatedStorageGb;

    @TableField("shared_storage_id")
    private String sharedStorageId;

    @TableField("san_storage_gb")
    private Integer sanStorageGb;

    @TableField("nas_storage_gb")
    private Integer nasStorageGb;

    @TableField("signature_server")
    private String signatureServer;

    @TableField("encryption_device")
    private String encryptionDevice;

    @TableField("load_balancer")
    private String loadBalancer;

    @TableField("ssl_accelerator")
    private String sslAccelerator;

    @TableField("remarks")
    private String remarks;

    @TableField("partition_role")
    private String partitionRole;

    @TableField("revision_time")
    private LocalDateTime revisionTime;

    @TableField("middleware_reason_below_baseline")
    private String middlewareReasonBelowBaseline;

    @TableField("os_reason_below_baseline")
    private String osReasonBelowBaseline;

    @TableField("resource_pool")
    private String resourcePool;

    @TableField("original_file_name")
    private String originalFileName;

    @TableField("file_name")
    private String fileName;

    @TableField("product_id")
    private String productId;

    @TableField("batch_no")
    private String batchNo;

    @TableField("file_source")
    private String fileSource;

    @TableField("create_time")
    private Date createTime;

    @TableField("create_operator")
    private String createOperator;

    @TableField("last_time")
    private Date lastTime;

    @TableField("last_operator")
    private String lastOperator;
}

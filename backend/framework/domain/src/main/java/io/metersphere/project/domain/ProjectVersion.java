package io.metersphere.project.domain;

import io.metersphere.validation.groups.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import lombok.Data;

@Data
public class ProjectVersion implements Serializable {
    @Schema(title = "版本ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{project_version.id.not_blank}", groups = {Updated.class})
    @Size(min = 1, max = 50, message = "{project_version.id.length_range}", groups = {Created.class, Updated.class})
    private String id;

    @Schema(title = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{project_version.project_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{project_version.project_id.length_range}", groups = {Created.class, Updated.class})
    private String projectId;

    @Schema(title = "版本名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{project_version.name.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 255, message = "{project_version.name.length_range}", groups = {Created.class, Updated.class})
    private String name;

    @Schema(title = "描述")
    private String description;

    @Schema(title = "状态")
    private String status;

    @Schema(title = "是否是最新版", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{project_version.latest.not_blank}", groups = {Created.class})
    private Boolean latest;

    @Schema(title = "发布时间")
    private Long publishTime;

    @Schema(title = "开始时间")
    private Long startTime;

    @Schema(title = "结束时间")
    private Long endTime;

    @Schema(title = "创建时间")
    private Long createTime;

    @Schema(title = "创建人")
    private String createUser;

    private static final long serialVersionUID = 1L;
}
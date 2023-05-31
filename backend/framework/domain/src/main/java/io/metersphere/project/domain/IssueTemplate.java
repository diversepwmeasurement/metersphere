package io.metersphere.project.domain;

import io.metersphere.validation.groups.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import lombok.Data;

@Data
public class IssueTemplate implements Serializable {
    @Schema(title = "ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{issue_template.id.not_blank}", groups = {Updated.class})
    @Size(min = 1, max = 50, message = "{issue_template.id.length_range}", groups = {Created.class, Updated.class})
    private String id;

    @Schema(title = "名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{issue_template.name.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 255, message = "{issue_template.name.length_range}", groups = {Created.class, Updated.class})
    private String name;

    @Schema(title = "描述")
    private String description;

    @Schema(title = "是否是系统模板", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "{issue_template.system.not_blank}", groups = {Created.class})
    private Boolean system;

    @Schema(title = "创建时间")
    private Long createTime;

    @Schema(title = "创建人")
    private String createUser;

    @Schema(title = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{issue_template.project_id.not_blank}", groups = {Created.class})
    @Size(min = 1, max = 50, message = "{issue_template.project_id.length_range}", groups = {Created.class, Updated.class})
    private String projectId;

    private static final long serialVersionUID = 1L;
}
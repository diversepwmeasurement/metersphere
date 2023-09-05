package io.metersphere.project.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * @author wx
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ProjectApplicationRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description =  "项目id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "{project_application.project_id.not_blank}")
    private String projectId;

    @Schema(description =  "配置类型列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "{project_application.type.not_blank}")
    private List<String> types;
}

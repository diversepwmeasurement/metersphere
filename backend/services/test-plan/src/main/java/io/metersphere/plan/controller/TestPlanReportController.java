package io.metersphere.plan.controller;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import io.metersphere.plan.constants.TestPlanResourceConfig;
import io.metersphere.plan.dto.request.TestPlanReportBatchRequest;
import io.metersphere.plan.dto.request.TestPlanReportPageRequest;
import io.metersphere.plan.dto.response.TestPlanReportPageResponse;
import io.metersphere.plan.service.TestPlanManagementService;
import io.metersphere.plan.service.TestPlanReportLogService;
import io.metersphere.plan.service.TestPlanReportNoticeService;
import io.metersphere.plan.service.TestPlanReportService;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.system.log.annotation.Log;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.notice.annotation.SendNotice;
import io.metersphere.system.notice.constants.NoticeConstants;
import io.metersphere.system.security.CheckOwner;
import io.metersphere.system.utils.PageUtils;
import io.metersphere.system.utils.Pager;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/test-plan/report")
@Tag(name = "测试计划-报告")
public class TestPlanReportController {

    @Resource
    private TestPlanManagementService testPlanManagementService;
    @Resource
    private TestPlanReportService testPlanReportService;

    @PostMapping("/page")
    @Operation(summary = "测试计划-报告-表格分页查询")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_REPORT_READ)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public Pager<List<TestPlanReportPageResponse>> page(@Validated @RequestBody TestPlanReportPageRequest request) {
        testPlanManagementService.checkModuleIsOpen(request.getProjectId(), TestPlanResourceConfig.CHECK_TYPE_PROJECT, Collections.singletonList(TestPlanResourceConfig.CONFIG_TEST_PLAN));
        Page<Object> page = PageHelper.startPage(request.getCurrent(), request.getPageSize(),
                StringUtils.isNotBlank(request.getSortString()) ? request.getSortString() : "tpr.create_time desc");
        return PageUtils.setPageInfo(page, testPlanReportService.page(request));
    }

    @PostMapping("/rename/{id}")
    @Operation(summary = "测试计划-报告-重命名")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_REPORT_READ_UPDATE)
    @CheckOwner(resourceId = "#request.getId()", resourceType = "test_plan_report")
    @Log(type = OperationLogType.UPDATE, expression = "#msClass.updateLog(#id)", msClass = TestPlanReportLogService.class)
    public void rename(@PathVariable String id, @RequestBody Object name) {
        testPlanReportService.rename(id, name.toString());
    }

    @GetMapping("/delete/{id}")
    @Operation(summary = "测试计划-报告-删除")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_REPORT_READ_DELETE)
    @CheckOwner(resourceId = "#id", resourceType = "test_plan_report")
    @Log(type = OperationLogType.DELETE, expression = "#msClass.deleteLog(#id)", msClass = TestPlanReportLogService.class)
    @SendNotice(taskType = NoticeConstants.TaskType.TEST_PLAN_REPORT_TASK, event = NoticeConstants.Event.DELETE, target = "#targetClass.getDto(#id)", targetClass = TestPlanReportNoticeService.class)
    public void delete(@PathVariable String id) {
        testPlanReportService.delete(id);
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "测试计划-报告-批量删除")
    @RequiresPermissions(PermissionConstants.TEST_PLAN_REPORT_READ_DELETE)
    @CheckOwner(resourceId = "#request.getProjectId()", resourceType = "project")
    public void batchDelete(@Validated @RequestBody TestPlanReportBatchRequest request) {
        testPlanReportService.batchDelete(request, SessionUtils.getUserId());
    }
}

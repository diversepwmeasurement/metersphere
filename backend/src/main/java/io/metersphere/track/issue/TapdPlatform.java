package io.metersphere.track.issue;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.metersphere.base.domain.IssuesDao;
import io.metersphere.base.domain.IssuesWithBLOBs;
import io.metersphere.base.domain.Project;
import io.metersphere.base.domain.TestCaseWithBLOBs;
import io.metersphere.commons.constants.IssuesManagePlatform;
import io.metersphere.commons.constants.IssuesStatus;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.controller.ResultHolder;
import io.metersphere.dto.UserDTO;
import io.metersphere.service.SystemParameterService;
import io.metersphere.track.dto.DemandDTO;
import io.metersphere.track.issue.client.TapdClient;
import io.metersphere.track.issue.domain.PlatformUser;
import io.metersphere.track.issue.domain.tapd.TapdBug;
import io.metersphere.track.issue.domain.tapd.TapdConfig;
import io.metersphere.track.issue.domain.tapd.TapdGetIssueResponse;
import io.metersphere.track.request.testcase.IssuesRequest;
import io.metersphere.track.request.testcase.IssuesUpdateRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TapdPlatform extends AbstractIssuePlatform {

    protected String key = IssuesManagePlatform.Tapd.toString();

    protected TapdClient tapdClient = new TapdClient();

    public TapdPlatform(IssuesRequest issueRequest) {
        super(issueRequest);
    }

    @Override
    public List<IssuesDao> getIssue(IssuesRequest issuesRequest) {
        issuesRequest.setPlatform(IssuesManagePlatform.Tapd.toString());
        List<IssuesDao> issues;
        if (StringUtils.isNotBlank(issuesRequest.getProjectId())) {
            issues = extIssuesMapper.getIssues(issuesRequest);
        } else {
            issues = extIssuesMapper.getIssuesByCaseId(issuesRequest);
        }
        return issues;
    }

    @Override
    public List<DemandDTO> getDemandList(String projectId) {
        List<DemandDTO> demandList = new ArrayList<>();
        try {
            String url = "https://api.tapd.cn/stories?workspace_id=" + getProjectId(projectId);
            ResultHolder call = call(url);
            String listJson = JSON.toJSONString(call.getData());
            JSONArray jsonArray = JSON.parseArray(listJson);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject o = jsonArray.getJSONObject(i);
                DemandDTO demand = o.getObject("Story", DemandDTO.class);
                demand.setPlatform(IssuesManagePlatform.Tapd.name());
                demandList.add(demand);
            }
        } catch (Exception e) {
            LogUtil.error(e);
        }

        return demandList;
    }

    @Override
    public void addIssue(IssuesUpdateRequest issuesRequest) {

        MultiValueMap<String, Object> param = buildUpdateParam(issuesRequest);
        TapdBug bug = tapdClient.addIssue(param);
        Map<String, String> statusMap = tapdClient.getStatusMap(getProjectId(this.projectId));
        issuesRequest.setPlatformStatus(statusMap.get(bug.getStatus()));

        issuesRequest.setPlatformId(bug.getId());
        issuesRequest.setId(UUID.randomUUID().toString());

        // 插入缺陷表
        insertIssues(issuesRequest);

        // 用例与第三方缺陷平台中的缺陷关联
        handleTestCaseIssues(issuesRequest);
    }

    @Override
    public void updateIssue(IssuesUpdateRequest request) {
        MultiValueMap<String, Object> param = buildUpdateParam(request);
        param.add("id", request.getPlatformId());
        handleIssueUpdate(request);
        tapdClient.updateIssue(param);
    }

    private MultiValueMap<String, Object> buildUpdateParam(IssuesUpdateRequest issuesRequest) {
        issuesRequest.setPlatform(IssuesManagePlatform.Tapd.toString());
        setConfig();

        String tapdId = getProjectId(issuesRequest.getProjectId());

        if (StringUtils.isBlank(tapdId)) {
            MSException.throwException("未关联Tapd 项目ID");
        }

        String usersStr = "";
        List<String> platformUsers = issuesRequest.getTapdUsers();
        if (CollectionUtils.isNotEmpty(platformUsers)) {
            usersStr = String.join(";", platformUsers);
        }

        String reporter = getReporter();
        if (StringUtils.isBlank(reporter)) {
            reporter = SessionUtils.getUser().getName();
        }

        MultiValueMap<String, Object> paramMap = new LinkedMultiValueMap<>();
        paramMap.add("title", issuesRequest.getTitle());
        paramMap.add("workspace_id", tapdId);
        paramMap.add("description", msDescription2Tapd(issuesRequest.getDescription()));
        paramMap.add("current_owner", usersStr);

        addCustomFields(issuesRequest, paramMap);

        paramMap.add("reporter", reporter);
        return paramMap;
    }

    private String msDescription2Tapd(String msDescription) {
        SystemParameterService parameterService = CommonBeanFactory.getBean(SystemParameterService.class);
        msDescription = msImg2HtmlImg(msDescription, parameterService.getValue("base.url"));
        return msDescription.replaceAll("\\n", "<br/>");
    }

    @Override
    public void deleteIssue(String id) {
        super.deleteIssue(id);
        // todo 暂无删除API
    }

    @Override
    public void testAuth() {
        try {
            String tapdConfig = getPlatformConfig(IssuesManagePlatform.Tapd.toString());
            JSONObject object = JSON.parseObject(tapdConfig);
            String account = object.getString("account");
            String password = object.getString("password");
            HttpHeaders headers = auth(account, password);
            HttpEntity<MultiValueMap> requestEntity = new HttpEntity<>(headers);
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.exchange("https://api.tapd.cn/quickstart/testauth", HttpMethod.GET, requestEntity, String.class);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            MSException.throwException("验证失败！");
        }
    }

    @Override
    public void userAuth(UserDTO.PlatformInfo userInfo) {
        testAuth();
    }

    @Override
    public List<PlatformUser> getPlatformUser() {
        List<PlatformUser> users = new ArrayList<>();
        String id = getProjectId(projectId);
        if (StringUtils.isBlank(id)) {
            MSException.throwException("未关联Tapd项目ID");
        }
        String url = "https://api.tapd.cn/workspaces/users?workspace_id=" + id;
        ResultHolder call = call(url);
        String listJson = JSON.toJSONString(call.getData());
        JSONArray jsonArray = JSON.parseArray(listJson);
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject o = jsonArray.getJSONObject(i);
            PlatformUser user = o.getObject("UserWorkspace", PlatformUser.class);
            users.add(user);
        }
        return users;
    }

    @Override
    public void syncIssues(Project project, List<IssuesDao> tapdIssues) {
        int pageNum = 1;
        int limit = 50;
        int count = 50;

        Map<String, String> idMap = tapdIssues.stream()
                .collect(Collectors.toMap(IssuesDao::getPlatformId, IssuesDao::getId));

        List<String> ids = tapdIssues.stream()
                .map(IssuesDao::getPlatformId)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        setConfig();

        Map<String, String> statusMap = tapdClient.getStatusMap(project.getTapdId());

        while (count == limit) {
            TapdGetIssueResponse result = tapdClient.getIssueForPageByIds(project.getTapdId(), pageNum, limit, ids);
            List<JSONObject> datas = result.getData();
            count = datas.size();
            pageNum++;
            datas.forEach(issue -> {
                JSONObject bug = issue.getJSONObject("Bug");
                String platformId = bug.getString("id");
                String id = idMap.get(platformId);
                IssuesWithBLOBs updateIssue = getUpdateIssue(issuesMapper.selectByPrimaryKey(id), bug, statusMap);
                updateIssue.setId(id);
                updateIssue.setCustomFields(syncIssueCustomField(updateIssue.getCustomFields(), bug));
                issuesMapper.updateByPrimaryKeySelective(updateIssue);
                ids.remove(platformId);
            });
        }
        // 查不到的设置为删除
        ids.forEach((id) -> {
            if (StringUtils.isNotBlank(idMap.get(id))) {
                IssuesDao issuesDao = new IssuesDao();
                issuesDao.setId(idMap.get(id));
                issuesDao.setPlatformStatus(IssuesStatus.DELETE.toString());
                issuesMapper.updateByPrimaryKeySelective(issuesDao);
            }
        });
    }

    protected IssuesWithBLOBs getUpdateIssue(IssuesWithBLOBs issue, JSONObject bug, Map<String, String> statusMap) {
        if (issue == null) issue = new IssuesWithBLOBs();
        TapdBug bugObj = JSONObject.parseObject(bug.toJSONString(), TapdBug.class);
        BeanUtils.copyBean(issue, bugObj);
        issue.setPlatformStatus(statusMap.get(bugObj.getStatus()));
        issue.setDescription(htmlDesc2MsDesc(issue.getDescription()));
        issue.setCustomFields(syncIssueCustomField(issue.getCustomFields(), bug));
        issue.setPlatform(key);
        return issue;
    }

    @Override
    public String getProjectId(String projectId) {
        if (StringUtils.isNotBlank(projectId)) {
            return projectService.getProjectById(projectId).getTapdId();
        }
        TestCaseWithBLOBs testCase = testCaseService.getTestCase(testCaseId);
        Project project = projectService.getProjectById(testCase.getProjectId());
        return project.getTapdId();
    }

    public TapdConfig getConfig() {
        String config = getPlatformConfig(IssuesManagePlatform.Tapd.toString());
        TapdConfig tapdConfig = JSONObject.parseObject(config, TapdConfig.class);
//        validateConfig(tapdConfig);
        return tapdConfig;
    }

    public String getReporter() {
        UserDTO.PlatformInfo userPlatInfo = getUserPlatInfo(this.workspaceId);
        if (userPlatInfo != null && StringUtils.isNotBlank(userPlatInfo.getTapdUserName())) {
            return userPlatInfo.getTapdUserName();
        }
        return null;
    }

    public TapdConfig setConfig() {
        TapdConfig config = getConfig();
        tapdClient.setConfig(config);
        return config;
    }

    private ResultHolder call(String url) {
        return call(url, HttpMethod.GET, null);
    }

    private ResultHolder call(String url, HttpMethod httpMethod, Object params) {
        String responseJson;

        String config = getPlatformConfig(IssuesManagePlatform.Tapd.toString());
        JSONObject object = JSON.parseObject(config);

        if (object == null) {
            MSException.throwException("tapd config is null");
        }

        String account = object.getString("account");
        String password = object.getString("password");

        HttpHeaders header = auth(account, password);

        if (httpMethod.equals(HttpMethod.GET)) {
            responseJson = TapdRestUtils.get(url, header);
        } else {
            responseJson = TapdRestUtils.post(url, params, header);
        }

        ResultHolder result = JSON.parseObject(responseJson, ResultHolder.class);

        if (!result.isSuccess()) {
            MSException.throwException(result.getMessage());
        }
        return JSON.parseObject(responseJson, ResultHolder.class);

    }


}

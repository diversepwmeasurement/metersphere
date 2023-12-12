package io.metersphere.functional.service;


import io.metersphere.functional.constants.CaseReviewStatus;
import io.metersphere.functional.constants.FunctionalCaseReviewStatus;
import io.metersphere.functional.domain.*;
import io.metersphere.functional.dto.BaseFunctionalCaseBatchDTO;
import io.metersphere.functional.dto.CaseReviewDTO;
import io.metersphere.functional.dto.CaseReviewUserDTO;
import io.metersphere.functional.mapper.*;
import io.metersphere.functional.request.*;
import io.metersphere.functional.result.CaseManagementResultCode;
import io.metersphere.project.dto.ModuleCountDTO;
import io.metersphere.sdk.constants.ApplicationNumScope;
import io.metersphere.sdk.constants.PermissionConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.BeanUtils;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.User;
import io.metersphere.system.dto.sdk.request.PosRequest;
import io.metersphere.system.mapper.ExtUserMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.uid.NumGenerator;
import io.metersphere.system.utils.ServiceUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用例评审表服务实现类
 *
 * @date : 2023-5-17
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class CaseReviewService {

    public static final int POS_STEP = 5000;

    @Resource
    private ExtCaseReviewMapper extCaseReviewMapper;
    @Resource
    private CaseReviewMapper caseReviewMapper;
    @Resource
    private CaseReviewFollowerMapper caseReviewFollowerMapper;
    @Resource
    SqlSessionFactory sqlSessionFactory;
    @Resource
    private CaseReviewUserMapper caseReviewUserMapper;
    @Resource
    private ExtUserMapper extUserMapper;
    @Resource
    private CaseReviewFunctionalCaseMapper caseReviewFunctionalCaseMapper;
    @Resource
    private ExtCaseReviewUserMapper extCaseReviewUserMapper;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private DeleteCaseReviewService deleteCaseReviewService;
    @Resource
    private CaseReviewFunctionalCaseUserMapper caseReviewFunctionalCaseUserMapper;
    @Resource
    private ExtCaseReviewFunctionalCaseMapper extCaseReviewFunctionalCaseMapper;
    @Resource
    private CaseReviewModuleService caseReviewModuleService;
    @Resource
    private ExtFunctionalCaseMapper extFunctionalCaseMapper;


    private static final String CASE_MODULE_COUNT_ALL = "all";

    /**
     * 获取用例评审列表
     *
     * @param request request
     * @return CaseReviewDTO
     */
    public List<CaseReviewDTO> getCaseReviewPage(CaseReviewPageRequest request) {
        List<CaseReviewDTO> list = extCaseReviewMapper.list(request);
        if (CollectionUtils.isEmpty(list)) {
            return new ArrayList<>();
        }
        List<String> reviewIds = list.stream().map(CaseReview::getId).toList();
        Map<String, List<CaseReviewFunctionalCase>> reviewCaseMap = getReviewCaseMap(reviewIds);
        Map<String, List<CaseReviewUserDTO>> reviewUserMap = getReviewUserMap(reviewIds);
        for (CaseReviewDTO caseReviewDTO : list) {
            buildCaseReviewDTO(caseReviewDTO, reviewCaseMap, reviewUserMap);
        }

        return list;
    }

    /**
     * 通过 reviewCaseMap reviewUserMap 补充 用例评审的其他属性
     *
     * @param caseReviewDTO caseReviewDTO
     * @param reviewCaseMap 用例和评审的关系map
     * @param reviewUserMap 评审和评审人的关系map
     */
    private static void buildCaseReviewDTO(CaseReviewDTO caseReviewDTO, Map<String, List<CaseReviewFunctionalCase>> reviewCaseMap, Map<String, List<CaseReviewUserDTO>> reviewUserMap) {
        String caseReviewId = caseReviewDTO.getId();
        List<CaseReviewFunctionalCase> caseReviewFunctionalCaseList = reviewCaseMap.get(caseReviewId);
        if (CollectionUtils.isEmpty(caseReviewFunctionalCaseList)) {
            caseReviewDTO.setPassCount(0);
            caseReviewDTO.setUnPassCount(0);
            caseReviewDTO.setReReviewedCount(0);
            caseReviewDTO.setUnderReviewedCount(0);
            caseReviewDTO.setReviewedCount(0);
        } else {
            buildAboutCaseCount(caseReviewDTO, caseReviewFunctionalCaseList);
        }

        buildReviewers(caseReviewDTO, reviewUserMap);
    }

    private static void buildReviewers(CaseReviewDTO caseReviewDTO, Map<String, List<CaseReviewUserDTO>> reviewUserMap) {
        List<CaseReviewUserDTO> caseReviewUserDTOS = reviewUserMap.get(caseReviewDTO.getId());
        List<String> userNames = caseReviewUserDTOS.stream().map(CaseReviewUserDTO::getUserName).toList();
        caseReviewDTO.setReviewers(userNames);
    }

    /**
     * 构建用例相关的各种数量
     *
     * @param caseReviewDTO                用例评审
     * @param caseReviewFunctionalCaseList 用例和评审相关联的集合
     */
    private static void buildAboutCaseCount(CaseReviewDTO caseReviewDTO, List<CaseReviewFunctionalCase> caseReviewFunctionalCaseList) {
        Map<String, List<CaseReviewFunctionalCase>> statusCaseMap = caseReviewFunctionalCaseList.stream().collect(Collectors.groupingBy(CaseReviewFunctionalCase::getStatus));

        List<CaseReviewFunctionalCase> passList = statusCaseMap.get(FunctionalCaseReviewStatus.PASS.toString());
        if (passList == null) {
            passList = new ArrayList<>();
        }
        caseReviewDTO.setPassCount(passList.size());

        List<CaseReviewFunctionalCase> unPassList = statusCaseMap.get(FunctionalCaseReviewStatus.UN_PASS.toString());
        if (unPassList == null) {
            unPassList = new ArrayList<>();
        }
        caseReviewDTO.setUnPassCount(unPassList.size());

        List<CaseReviewFunctionalCase> reReviewedList = statusCaseMap.get(FunctionalCaseReviewStatus.RE_REVIEWED.toString());
        if (reReviewedList == null) {
            reReviewedList = new ArrayList<>();
        }
        caseReviewDTO.setReReviewedCount(reReviewedList.size());

        List<CaseReviewFunctionalCase> underReviewedList = statusCaseMap.get(FunctionalCaseReviewStatus.UNDER_REVIEWED.toString());
        if (underReviewedList == null) {
            underReviewedList = new ArrayList<>();
        }
        caseReviewDTO.setUnderReviewedCount(underReviewedList.size());

        caseReviewDTO.setReviewedCount(caseReviewDTO.getPassCount() + caseReviewDTO.getUnPassCount());
    }

    /**
     * 通过评审ids获取评审和评审人的关系map
     *
     * @param reviewIds 评审ids
     * @return Map
     */
    private Map<String, List<CaseReviewUserDTO>> getReviewUserMap(List<String> reviewIds) {
        List<CaseReviewUserDTO> reviewUser = extCaseReviewUserMapper.getReviewUser(reviewIds);
        return reviewUser.stream().collect(Collectors.groupingBy(CaseReviewUserDTO::getReviewId));
    }


    /**
     * 通过评审ids获取用例和评审的关系map
     *
     * @param reviewIds 评审ids
     * @return Map
     */
    private Map<String, List<CaseReviewFunctionalCase>> getReviewCaseMap(List<String> reviewIds) {
        CaseReviewFunctionalCaseExample caseReviewFunctionalCaseExample = new CaseReviewFunctionalCaseExample();
        caseReviewFunctionalCaseExample.createCriteria().andReviewIdIn(reviewIds);
        List<CaseReviewFunctionalCase> caseReviewFunctionalCases = caseReviewFunctionalCaseMapper.selectByExample(caseReviewFunctionalCaseExample);
        return caseReviewFunctionalCases.stream().collect(Collectors.groupingBy(CaseReviewFunctionalCase::getReviewId));
    }


    /**
     * 添加用例评审
     *
     * @param request 页面参数
     * @param userId  当前操作人
     */
    public void addCaseReview(CaseReviewRequest request, String userId) {
        String caseReviewId = IDGenerator.nextStr();
        BaseAssociateCaseRequest baseAssociateCaseRequest = request.getBaseAssociateCaseRequest();
        List<String> caseIds = doSelectIds(baseAssociateCaseRequest, baseAssociateCaseRequest.getProjectId());
        addCaseReview(request, userId, caseReviewId, caseIds);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        CaseReviewUserMapper mapper = sqlSession.getMapper(CaseReviewUserMapper.class);
        CaseReviewFunctionalCaseMapper caseReviewFunctionalCaseMapper = sqlSession.getMapper(CaseReviewFunctionalCaseMapper.class);
        CaseReviewFunctionalCaseUserMapper caseReviewFunctionalCaseUserMapper = sqlSession.getMapper(CaseReviewFunctionalCaseUserMapper.class);
        try {
            //保存和评审人的关系
            addCaseReviewUser(request, caseReviewId, mapper);
            //保存和用例的关系
            addCaseReviewFunctionalCase(caseIds, request.getProjectId(), userId, caseReviewId, caseReviewFunctionalCaseMapper);
            //保存用例和用例评审人的关系
            addCaseReviewFunctionalCaseUser(caseIds, request.getReviewers(), caseReviewId, caseReviewFunctionalCaseUserMapper);
            sqlSession.flushStatements();
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    /**
     * 保存用例和用例评审人的关系
     *
     * @param caseIds                            caseIds
     * @param reviewers                          reviewers
     * @param caseReviewId                       当前用例评审id
     * @param caseReviewFunctionalCaseUserMapper mapper
     */
    private static void addCaseReviewFunctionalCaseUser(List<String> caseIds, List<String> reviewers, String caseReviewId, CaseReviewFunctionalCaseUserMapper caseReviewFunctionalCaseUserMapper) {
        if (CollectionUtils.isNotEmpty(caseIds)) {
            caseIds.forEach(caseId -> {
                reviewers.forEach(reviewer -> {
                    CaseReviewFunctionalCaseUser caseReviewFunctionalCaseUser = new CaseReviewFunctionalCaseUser();
                    caseReviewFunctionalCaseUser.setCaseId(caseId);
                    caseReviewFunctionalCaseUser.setUserId(reviewer);
                    caseReviewFunctionalCaseUser.setReviewId(caseReviewId);
                    caseReviewFunctionalCaseUserMapper.insert(caseReviewFunctionalCaseUser);
                });
            });
        }
    }

    /**
     * 编辑用例评审
     *
     * @param request 页面参数
     * @param userId  当前操作人
     */
    public void editCaseReview(CaseReviewRequest request, String userId) {
        String reviewId = request.getId();
        checkCaseReview(reviewId);
        CaseReview caseReview = new CaseReview();
        caseReview.setId(reviewId);
        caseReview.setProjectId(request.getProjectId());
        caseReview.setName(request.getName());
        caseReview.setModuleId(request.getModuleId());
        if (CollectionUtils.isNotEmpty(request.getTags())) {
            caseReview.setTags(JSON.toJSONString(request.getTags()));
        }
        caseReview.setStartTime(request.getStartTime());
        caseReview.setEndTime(request.getEndTime());
        caseReview.setUpdateTime(System.currentTimeMillis());
        caseReview.setUpdateUser(userId);
        caseReviewMapper.updateByPrimaryKeySelective(caseReview);
        //删除用例评审和评审人的关系
        CaseReviewUserExample caseReviewUserExample = new CaseReviewUserExample();
        caseReviewUserExample.createCriteria().andReviewIdEqualTo(reviewId);
        caseReviewUserMapper.deleteByExample(caseReviewUserExample);
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        CaseReviewUserMapper mapper = sqlSession.getMapper(CaseReviewUserMapper.class);
        try {
            //保存评审和评审人的关系
            addCaseReviewUser(request, reviewId, mapper);
            sqlSession.flushStatements();
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    /**
     * 关注/取消关注用例
     *
     * @param caseReviewId 用例评审id
     * @param userId       当前操作人
     */
    public void editFollower(String caseReviewId, String userId) {
        checkCaseReview(caseReviewId);
        CaseReviewFollowerExample example = new CaseReviewFollowerExample();
        example.createCriteria().andReviewIdEqualTo(caseReviewId).andUserIdEqualTo(userId);
        if (caseReviewFollowerMapper.countByExample(example) > 0) {
            caseReviewFollowerMapper.deleteByPrimaryKey(caseReviewId, userId);
        } else {
            CaseReviewFollower caseReviewFollower = new CaseReviewFollower();
            caseReviewFollower.setReviewId(caseReviewId);
            caseReviewFollower.setUserId(userId);
            caseReviewFollowerMapper.insert(caseReviewFollower);
        }
    }

    /**
     * 获取具有评审权限的用户
     *
     * @param projectId projectId
     * @param keyword   查询关键字，根据邮箱和用户名查询
     * @return List<User>
     */
    public List<User> getReviewUserList(String projectId, String keyword) {
        return extUserMapper.getUserByPermission(projectId, keyword, PermissionConstants.CASE_REVIEW_REVIEW);

    }

    /**
     * 新增用例评审
     *
     * @param request      request
     * @param userId       当前操作人
     * @param caseReviewId 用例评审id
     */
    private void addCaseReview(CaseReviewRequest request, String userId, String caseReviewId, List<String> caseIds) {
        CaseReview caseReview = new CaseReview();
        caseReview.setId(caseReviewId);
        caseReview.setNum(getNextNum(request.getProjectId()));
        caseReview.setProjectId(request.getProjectId());
        caseReview.setName(request.getName());
        caseReview.setModuleId(request.getModuleId());
        caseReview.setStatus(CaseReviewStatus.PREPARED.toString());
        caseReview.setReviewPassRule(request.getReviewPassRule());
        caseReview.setPos(getNextPos(request.getProjectId()));
        if (CollectionUtils.isNotEmpty(request.getTags())) {
            caseReview.setTags(JSON.toJSONString(request.getTags()));
        }
        caseReview.setPassRate(BigDecimal.valueOf(0.00));
        if (CollectionUtils.isEmpty(caseIds)) {
            caseReview.setCaseCount(0);
        } else {
            caseReview.setCaseCount(caseIds.size());
        }
        caseReview.setStartTime(request.getStartTime());
        caseReview.setEndTime(request.getEndTime());
        caseReview.setCreateTime(System.currentTimeMillis());
        caseReview.setUpdateTime(System.currentTimeMillis());
        caseReview.setCreateUser(userId);
        caseReview.setUpdateUser(userId);
        caseReviewMapper.insert(caseReview);
    }

    /**
     * 检查用例评审是否存在
     *
     * @param caseReviewId 用例评审id
     */
    private CaseReview checkCaseReview(String caseReviewId) {
        CaseReview caseReview = caseReviewMapper.selectByPrimaryKey(caseReviewId);
        if (caseReview == null) {
            throw new MSException(CaseManagementResultCode.CASE_REVIEW_NOT_FOUND);
        }
        return caseReview;
    }

    /**
     * @param projectId 项目id
     * @return pos
     */
    public Long getNextPos(String projectId) {
        Long pos = extCaseReviewMapper.getPos(projectId);
        return (pos == null ? 0 : pos) + POS_STEP;
    }

    /**
     * @param caseReviewId 用例评审id
     * @return pos
     */
    public Long getCaseFunctionalCaseNextPos(String caseReviewId) {
        Long pos = extCaseReviewFunctionalCaseMapper.getPos(caseReviewId);
        return (pos == null ? 0 : pos) + POS_STEP;
    }

    /**
     * @param projectId 项目id
     * @return num
     */
    public long getNextNum(String projectId) {
        return NumGenerator.nextNum(projectId, ApplicationNumScope.CASE_MANAGEMENT);
    }

    /**
     * 保存用例评审和功能用例的关系
     *
     * @param caseIds                        功能用例Ids
     * @param projectId                      项目ID
     * @param userId                         当前操作人
     * @param caseReviewId                   用例评审id
     * @param caseReviewFunctionalCaseMapper caseReviewFunctionalCaseMapper
     */
    private void addCaseReviewFunctionalCase(List<String> caseIds, String projectId, String userId, String caseReviewId, CaseReviewFunctionalCaseMapper caseReviewFunctionalCaseMapper) {
        if (CollectionUtils.isNotEmpty(caseIds)) {
            caseIds.forEach(caseId -> {
                CaseReviewFunctionalCase caseReviewFunctionalCase = new CaseReviewFunctionalCase();
                caseReviewFunctionalCase.setReviewId(caseReviewId);
                caseReviewFunctionalCase.setCaseId(caseId);
                caseReviewFunctionalCase.setStatus(FunctionalCaseReviewStatus.UN_REVIEWED.toString());
                caseReviewFunctionalCase.setCreateUser(userId);
                caseReviewFunctionalCase.setCreateTime(System.currentTimeMillis());
                caseReviewFunctionalCase.setUpdateTime(System.currentTimeMillis());
                caseReviewFunctionalCase.setId(IDGenerator.nextStr());
                caseReviewFunctionalCase.setPos(getCaseFunctionalCaseNextPos(caseReviewId));
                caseReviewFunctionalCaseMapper.insert(caseReviewFunctionalCase);
            });
        }
    }

    /**
     * 保存用例评审和评审人的关系
     *
     * @param request              request
     * @param caseReviewId         用例评审
     * @param caseReviewUserMapper caseReviewUserMapper
     */
    private static void addCaseReviewUser(CaseReviewRequest request, String caseReviewId, CaseReviewUserMapper caseReviewUserMapper) {
        request.getReviewers().forEach(user -> {
            CaseReviewUser caseReviewUser = new CaseReviewUser();
            caseReviewUser.setReviewId(caseReviewId);
            caseReviewUser.setUserId(user);
            caseReviewUserMapper.insert(caseReviewUser);
        });
    }


    /**
     * 关联用例
     *
     * @param request 页面参数
     * @param userId  当前操作人
     */
    public void associateCase(CaseReviewAssociateRequest request, String userId) {
        String caseReviewId = request.getReviewId();
        CaseReview caseReviewExist = checkCaseReview(caseReviewId);
        BaseAssociateCaseRequest baseAssociateCaseRequest = request.getBaseAssociateCaseRequest();
        List<String> caseIds = doSelectIds(baseAssociateCaseRequest, baseAssociateCaseRequest.getProjectId());
        if (CollectionUtils.isEmpty(caseIds)) {
            return;
        }
        FunctionalCaseExample functionalCaseExample = new FunctionalCaseExample();
        functionalCaseExample.createCriteria().andIdIn(caseIds);
        List<FunctionalCase> functionalCases = functionalCaseMapper.selectByExample(functionalCaseExample);
        if (CollectionUtils.isEmpty(functionalCases)) {
            return;
        }
        CaseReviewFunctionalCaseExample caseReviewFunctionalCaseExample = new CaseReviewFunctionalCaseExample();
        caseReviewFunctionalCaseExample.createCriteria().andReviewIdEqualTo(caseReviewId);
        List<CaseReviewFunctionalCase> caseReviewFunctionalCases = caseReviewFunctionalCaseMapper.selectByExample(caseReviewFunctionalCaseExample);
        List<String> castIds = caseReviewFunctionalCases.stream().map(CaseReviewFunctionalCase::getCaseId).toList();
        List<String> caseRealIds = caseIds.stream().filter(t -> !castIds.contains(t)).toList();
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        CaseReviewFunctionalCaseMapper caseReviewFunctionalCaseMapper = sqlSession.getMapper(CaseReviewFunctionalCaseMapper.class);
        CaseReviewFunctionalCaseUserMapper caseReviewFunctionalCaseUserMapper = sqlSession.getMapper(CaseReviewFunctionalCaseUserMapper.class);
        try {
            //保存和用例的关系
            addCaseReviewFunctionalCase(caseRealIds, request.getProjectId(), userId, caseReviewId, caseReviewFunctionalCaseMapper);
            //保存用例和用例评审人的关系
            addCaseReviewFunctionalCaseUser(caseRealIds, request.getReviewers(), caseReviewId, caseReviewFunctionalCaseUserMapper);
            sqlSession.flushStatements();
        } finally {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
        List<CaseReviewFunctionalCase> passList = caseReviewFunctionalCases.stream().filter(t -> StringUtils.equalsIgnoreCase(t.getStatus(), FunctionalCaseReviewStatus.PASS.toString())).toList();
        CaseReview caseReview = new CaseReview();
        caseReview.setId(caseReviewId);
        //更新用例数量
        caseReview.setCaseCount(caseReviewExist.getCaseCount() + caseRealIds.size());
        //通过率
        BigDecimal passCount = BigDecimal.valueOf(passList.size());
        BigDecimal totalCount = BigDecimal.valueOf(caseReview.getCaseCount());
        BigDecimal passRate = passCount.divide(totalCount, 2, RoundingMode.HALF_UP);
        caseReview.setPassRate(passRate);
        caseReviewMapper.updateByPrimaryKeySelective(caseReview);
    }

    public <T> List<String> doSelectIds(T dto, String projectId) {
        BaseFunctionalCaseBatchDTO request = (BaseFunctionalCaseBatchDTO) dto;
        if (request.isSelectAll()) {
            List<String> ids = extFunctionalCaseMapper.getIds(request, projectId, false);
            if (CollectionUtils.isNotEmpty(request.getExcludeIds())) {
                ids.removeAll(request.getExcludeIds());
            }
            return ids;
        } else {
            return request.getSelectIds();
        }
    }

    /**
     * 用例评审列表拖拽排序
     *
     * @param request request
     */
    public void editPos(PosRequest request) {
        ServiceUtils.updatePosField(request,
                CaseReview.class,
                caseReviewMapper::selectByPrimaryKey,
                extCaseReviewMapper::getPrePos,
                extCaseReviewMapper::getLastPos,
                caseReviewMapper::updateByPrimaryKeySelective);
    }

    /**
     * 获取用例评审详情
     *
     * @param id     用例评审id
     * @param userId 当前操作人
     * @return CaseReviewDTO
     */
    public CaseReviewDTO getCaseReviewDetail(String id, String userId) {
        CaseReview caseReview = checkCaseReview(id);
        CaseReviewDTO caseReviewDTO = new CaseReviewDTO();
        BeanUtils.copyBean(caseReviewDTO, caseReview);
        Boolean isFollow = checkFollow(id, userId);
        caseReviewDTO.setFollowFlag(isFollow);
        Map<String, List<CaseReviewFunctionalCase>> reviewCaseMap = getReviewCaseMap(List.of(id));
        Map<String, List<CaseReviewUserDTO>> reviewUserMap = getReviewUserMap(List.of(id));
        buildCaseReviewDTO(caseReviewDTO, reviewCaseMap, reviewUserMap);
        return caseReviewDTO;
    }

    /**
     * 检查当前操作人是否关注该用例评审
     *
     * @param id     评审人名称
     * @param userId 操作人
     * @return Boolean
     */
    private Boolean checkFollow(String id, String userId) {
        CaseReviewFollowerExample caseReviewFollowerExample = new CaseReviewFollowerExample();
        caseReviewFollowerExample.createCriteria().andReviewIdEqualTo(id).andUserIdEqualTo(userId);
        return caseReviewFollowerMapper.countByExample(caseReviewFollowerExample) > 0;
    }

    public void batchMoveCaseReview(CaseReviewBatchRequest request, String userId) {
        List<String> ids;
        if (request.isSelectAll()) {
            ids = extCaseReviewMapper.getIds(request, request.getProjectId());
            if (CollectionUtils.isNotEmpty(request.getExcludeIds())) {
                ids.removeAll(request.getExcludeIds());
            }

        } else {
            ids = request.getSelectIds();
        }
        if (CollectionUtils.isNotEmpty(ids)) {
            extCaseReviewMapper.batchMoveModule(request, ids, userId);
        }
    }

    public void deleteCaseReview(String reviewId, String projectId) {
        deleteCaseReviewService.deleteCaseReviewResource(List.of(reviewId), projectId, false);

    }

    public void disassociate(String reviewId, String caseId) {
        //1.刪除评审与功能用例关联关系
        CaseReviewFunctionalCaseExample caseReviewFunctionalCaseExample = new CaseReviewFunctionalCaseExample();
        caseReviewFunctionalCaseExample.createCriteria().andReviewIdEqualTo(reviewId).andCaseIdEqualTo(caseId);
        caseReviewFunctionalCaseMapper.deleteByExample(caseReviewFunctionalCaseExample);
        //2.删除用例和用例评审人的关系
        CaseReviewFunctionalCaseUserExample caseReviewFunctionalCaseUserExample = new CaseReviewFunctionalCaseUserExample();
        caseReviewFunctionalCaseUserExample.createCriteria().andCaseIdEqualTo(caseId).andReviewIdEqualTo(reviewId);
        caseReviewFunctionalCaseUserMapper.deleteByExample(caseReviewFunctionalCaseUserExample);

        caseReviewFunctionalCaseExample = new CaseReviewFunctionalCaseExample();
        caseReviewFunctionalCaseExample.createCriteria().andReviewIdEqualTo(reviewId);
        List<CaseReviewFunctionalCase> caseReviewFunctionalCases = caseReviewFunctionalCaseMapper.selectByExample(caseReviewFunctionalCaseExample);
        List<CaseReviewFunctionalCase> passList = caseReviewFunctionalCases.stream().filter(t -> StringUtils.equalsIgnoreCase(t.getStatus(), FunctionalCaseReviewStatus.PASS.toString())).toList();
        CaseReview caseReview = new CaseReview();
        caseReview.setId(reviewId);
        //更新用例数量
        caseReview.setCaseCount(caseReviewFunctionalCases.size());
        //通过率
        BigDecimal passCount = BigDecimal.valueOf(passList.size());
        BigDecimal totalCount = BigDecimal.valueOf(caseReview.getCaseCount());
        BigDecimal passRate;
        if (totalCount.compareTo(BigDecimal.ZERO)==0) {
            passRate = BigDecimal.ZERO;
        } else {
            passRate = passCount.divide(totalCount, 2, RoundingMode.HALF_UP);
        }
        caseReview.setPassRate(passRate);
        caseReviewMapper.updateByPrimaryKeySelective(caseReview);
    }

    public Map<String, Long> moduleCount(CaseReviewPageRequest request) {
        //查出每个模块节点下的资源数量。 不需要按照模块进行筛选
        List<ModuleCountDTO> moduleCountDTOList = extCaseReviewMapper.countModuleIdByKeywordAndFileType(request);
        Map<String, Long> moduleCountMap = caseReviewModuleService.getModuleCountMap(request.getProjectId(), moduleCountDTOList);
        //查出全部用例数量
        long allCount = extCaseReviewMapper.caseCount(request);
        moduleCountMap.put(CASE_MODULE_COUNT_ALL, allCount);
        return moduleCountMap;
    }
}
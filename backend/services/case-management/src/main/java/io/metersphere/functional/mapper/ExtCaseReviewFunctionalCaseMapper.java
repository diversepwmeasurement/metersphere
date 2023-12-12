package io.metersphere.functional.mapper;

import io.metersphere.functional.dto.FunctionalCaseReviewDTO;
import io.metersphere.functional.dto.ReviewFunctionalCaseDTO;
import io.metersphere.functional.request.BaseReviewCaseBatchRequest;
import io.metersphere.functional.request.FunctionalCaseReviewListRequest;
import io.metersphere.functional.request.ReviewFunctionalCasePageRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author guoyuqi
 */
public interface ExtCaseReviewFunctionalCaseMapper {

    List<FunctionalCaseReviewDTO> list(@Param("request") FunctionalCaseReviewListRequest request);

    void updateStatus(@Param("caseId") String caseId, @Param("reviewId") String reviewId, @Param("status") String status);

    Long getUnCompletedCaseCount(@Param("reviewId") String reviewId, @Param("statusList") List<String> statusList);

    List<String> getCaseIdsByReviewId(@Param("reviewId") String reviewId);

    List<ReviewFunctionalCaseDTO> page(@Param("request") ReviewFunctionalCasePageRequest request, @Param("deleted") boolean deleted, @Param("userId") String userId, @Param("sort") String sort);

    Long getPos(@Param("reviewId") String reviewId);

    List<String> getIds(@Param("request") BaseReviewCaseBatchRequest request, @Param("userId") String userId, @Param("deleted") boolean deleted);
}

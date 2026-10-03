package com.yuka.learning.exam;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.ClientZone;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.exam.dto.ExamProfileResponse;
import com.yuka.learning.exam.dto.SyllabusResponse;
import com.yuka.learning.exam.dto.UpdateExamProfileRequest;
import com.yuka.learning.exam.syllabus.Syllabus;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exam")
public class ExamController {

    private final Syllabus syllabus;
    private final ExamProfileService profileService;

    public ExamController(Syllabus syllabus, ExamProfileService profileService) {
        this.syllabus = syllabus;
        this.profileService = profileService;
    }

    /** The whole syllabus. Identical for every user; clients cache it for the session. */
    @GetMapping("/syllabus")
    public ApiResponse<SyllabusResponse> syllabus() {
        return ApiResponse.success(SyllabusResponse.from(syllabus));
    }

    @GetMapping("/profile")
    public ApiResponse<ExamProfileResponse> profile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestHeader(value = ClientZone.HEADER, required = false) String zone) {
        return ApiResponse.success(profileService.get(principal.id(), ClientZone.resolve(zone)));
    }

    @PutMapping("/profile")
    public ApiResponse<ExamProfileResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @RequestHeader(value = ClientZone.HEADER, required = false) String zone,
                                                          @Valid @RequestBody UpdateExamProfileRequest request) {
        return ApiResponse.success(profileService.update(principal.id(), request, ClientZone.resolve(zone)));
    }
}

package com.yuka.ailearningserver.material;

import com.yuka.ailearningserver.auth.security.AuthenticatedUser;
import com.yuka.ailearningserver.common.api.ApiResponse;
import com.yuka.ailearningserver.material.dto.CreateMaterialRequest;
import com.yuka.ailearningserver.material.dto.MaterialResponse;
import com.yuka.ailearningserver.material.dto.UpdateMaterialRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public ApiResponse<List<MaterialResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestParam Long subjectId) {
        return ApiResponse.success(materialService.listBySubject(principal.id(), subjectId));
    }

    @PostMapping
    public ApiResponse<MaterialResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @Valid @RequestBody CreateMaterialRequest request) {
        return ApiResponse.success(materialService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable Long id,
                                                @Valid @RequestBody UpdateMaterialRequest request) {
        return ApiResponse.success(materialService.update(principal.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        materialService.delete(principal.id(), id);
        return ApiResponse.success();
    }
}

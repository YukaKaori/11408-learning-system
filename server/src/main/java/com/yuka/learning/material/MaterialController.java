package com.yuka.learning.material;

import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.material.dto.CreateMaterialRequest;
import com.yuka.learning.material.dto.MaterialResponse;
import com.yuka.learning.material.dto.UpdateMaterialRequest;
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

/**
 * Materials — the candidate's reference shelf (教材, 视频课, 真题 PDFs, links),
 * anchored to syllabus nodes. Flat routes: a material is addressed by id, and
 * the collection is filtered by a syllabus scope rather than nested under a
 * parent row (there is no per-user parent any more — the syllabus is global).
 */
@RestController
@RequestMapping("/api/v1/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    /** {@code nodeCode}: optional scope — the node and everything beneath it. */
    @GetMapping
    public ApiResponse<List<MaterialResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @RequestParam(required = false) String nodeCode) {
        return ApiResponse.success(materialService.list(principal.id(), nodeCode));
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

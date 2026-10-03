package com.yuka.learning.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.OwnershipGuard;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.material.dto.CreateMaterialRequest;
import com.yuka.learning.material.dto.MaterialResponse;
import com.yuka.learning.material.dto.UpdateMaterialRequest;
import com.yuka.learning.material.entity.LearningMaterial;
import com.yuka.learning.material.entity.MaterialType;
import com.yuka.learning.material.mapper.LearningMaterialMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * The candidate's reference shelf. Metadata and external links only — uploads
 * arrive with the future StorageService ({@code storageKey}/{@code sizeBytes}
 * stay reserved). A material may be anchored to any syllabus node (a whole
 * paper for a textbook, a chapter for a lecture video) or to none.
 */
@Service
public class MaterialService {

    private final LearningMaterialMapper materialMapper;
    private final Syllabus syllabus;

    public MaterialService(LearningMaterialMapper materialMapper, Syllabus syllabus) {
        this.materialMapper = materialMapper;
        this.syllabus = syllabus;
    }

    /**
     * The caller's materials, newest first. With a {@code nodeCode} the list is
     * the node's subtree <em>plus its ancestors</em>: a textbook filed under the
     * whole of 408 is still reference material for 进程同步.
     */
    public List<MaterialResponse> list(Long userId, String nodeCode) {
        LambdaQueryWrapper<LearningMaterial> query = new LambdaQueryWrapper<LearningMaterial>()
                .eq(LearningMaterial::getUserId, userId)
                .orderByDesc(LearningMaterial::getCreatedAt);
        if (nodeCode != null && !nodeCode.isBlank()) {
            String scope = syllabus.require(nodeCode.trim()).code();
            List<String> ancestors = syllabus.path(scope).stream().map(node -> node.code()).toList();
            query.and(q -> q.in(LearningMaterial::getNodeCode, ancestors)
                    .or().likeRight(LearningMaterial::getNodeCode, scope + "."));
        }
        return materialMapper.selectList(query).stream().map(MaterialResponse::from).toList();
    }

    public MaterialResponse create(Long userId, CreateMaterialRequest request) {
        LearningMaterial material = new LearningMaterial();
        material.setUserId(userId);
        material.setNodeCode(syllabus.resolve(request.nodeCode()));
        material.setTitle(request.title());
        material.setType(parseType(request.type()));
        material.setDescription(request.description());
        material.setSourceUrl(request.sourceUrl());
        materialMapper.insert(material);
        return MaterialResponse.from(material);
    }

    public MaterialResponse update(Long userId, Long id, UpdateMaterialRequest request) {
        LearningMaterial material = requireOwned(userId, id);
        if (request.title() != null && !request.title().isBlank()) {
            material.setTitle(request.title());
        }
        if (request.type() != null) {
            material.setType(parseType(request.type()));
        }
        if (request.description() != null) {
            material.setDescription(request.description());
        }
        if (request.sourceUrl() != null) {
            material.setSourceUrl(request.sourceUrl());
        }
        if (request.nodeCode() != null) {
            material.setNodeCode(syllabus.resolve(request.nodeCode()));
        }
        materialMapper.updateById(material);
        return MaterialResponse.from(material);
    }

    public void delete(Long userId, Long id) {
        LearningMaterial material = requireOwned(userId, id);
        materialMapper.deleteById(material.getId());
    }

    private static MaterialType parseType(String value) {
        try {
            return MaterialType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_TYPE_INVALID);
        }
    }

    private LearningMaterial requireOwned(Long userId, Long id) {
        return OwnershipGuard.require(materialMapper.selectById(id), LearningMaterial::getUserId, userId,
                MaterialErrorCode.MATERIAL_NOT_FOUND, MaterialErrorCode.MATERIAL_ACCESS_DENIED);
    }
}

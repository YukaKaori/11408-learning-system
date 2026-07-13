package com.yuka.ailearningserver.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.ailearningserver.activity.ActivityService;
import com.yuka.ailearningserver.activity.entity.ActivityType;
import com.yuka.ailearningserver.common.exception.BusinessException;
import com.yuka.ailearningserver.material.dto.CreateMaterialRequest;
import com.yuka.ailearningserver.material.dto.MaterialResponse;
import com.yuka.ailearningserver.material.dto.UpdateMaterialRequest;
import com.yuka.ailearningserver.material.entity.LearningMaterial;
import com.yuka.ailearningserver.material.entity.MaterialType;
import com.yuka.ailearningserver.material.mapper.LearningMaterialMapper;
import com.yuka.ailearningserver.subject.SubjectErrorCode;
import com.yuka.ailearningserver.subject.entity.Subject;
import com.yuka.ailearningserver.subject.mapper.SubjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Learning material CRUD, scoped under a subject. Ownership is enforced twice
 * over: the parent subject must belong to the caller on create, and the
 * denormalized {@code user_id} guards every later read/write.
 */
@Service
public class MaterialService {

    private final LearningMaterialMapper materialMapper;
    private final SubjectMapper subjectMapper;
    private final ActivityService activityService;

    public MaterialService(LearningMaterialMapper materialMapper, SubjectMapper subjectMapper,
                           ActivityService activityService) {
        this.materialMapper = materialMapper;
        this.subjectMapper = subjectMapper;
        this.activityService = activityService;
    }

    public List<MaterialResponse> listBySubject(Long userId, Long subjectId) {
        return materialMapper.selectList(new LambdaQueryWrapper<LearningMaterial>()
                        .eq(LearningMaterial::getUserId, userId)
                        .eq(LearningMaterial::getSubjectId, subjectId)
                        .orderByDesc(LearningMaterial::getCreatedAt))
                .stream()
                .map(MaterialResponse::from)
                .toList();
    }

    public MaterialResponse create(Long userId, CreateMaterialRequest request) {
        Subject subject = requireOwnedSubject(userId, request.subjectId());
        LearningMaterial material = new LearningMaterial();
        material.setUserId(userId);
        material.setSubjectId(subject.getId());
        material.setTitle(request.title().strip());
        material.setType(parseType(request.type()));
        material.setDescription(request.description());
        material.setSourceUrl(request.sourceUrl());
        materialMapper.insert(material);
        activityService.record(userId, ActivityType.MATERIAL_UPLOADED, subject.getId(), material.getId(),
                material.getTitle());
        return MaterialResponse.from(material);
    }

    public MaterialResponse update(Long userId, Long id, UpdateMaterialRequest request) {
        LearningMaterial material = requireOwned(userId, id);
        if (request.title() != null && !request.title().isBlank()) {
            material.setTitle(request.title().strip());
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
        materialMapper.updateById(material);
        return MaterialResponse.from(material);
    }

    public void delete(Long userId, Long id) {
        LearningMaterial material = requireOwned(userId, id);
        materialMapper.deleteById(material.getId());
    }

    private static MaterialType parseType(String token) {
        try {
            return MaterialType.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_INVALID_TYPE);
        }
    }

    private Subject requireOwnedSubject(Long userId, Long subjectId) {
        Subject subject = subjectMapper.selectById(subjectId);
        if (subject == null) {
            throw new BusinessException(SubjectErrorCode.SUBJECT_NOT_FOUND);
        }
        if (!subject.getUserId().equals(userId)) {
            throw new BusinessException(SubjectErrorCode.SUBJECT_ACCESS_DENIED);
        }
        return subject;
    }

    private LearningMaterial requireOwned(Long userId, Long id) {
        LearningMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND);
        }
        if (!material.getUserId().equals(userId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_ACCESS_DENIED);
        }
        return material;
    }
}

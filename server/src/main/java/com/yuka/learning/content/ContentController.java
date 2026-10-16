package com.yuka.learning.content;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.common.api.ApiResponse;
import com.yuka.learning.content.entity.ContentPackRecord;
import com.yuka.learning.content.mapper.ContentPackMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;

/**
 * Which content is live — the pack versions this deployment serves. Read-only
 * and the same for every candidate: content changes only through packs.
 */
@RestController
@RequestMapping("/api/v1/content")
public class ContentController {

    private final ContentPackMapper packMapper;

    public ContentController(ContentPackMapper packMapper) {
        this.packMapper = packMapper;
    }

    /**
     * @param source     {@code classpath} | {@code directory}
     * @param importedAt epoch ms when this version became live
     */
    public record PackResponse(String pack, String title, int version, int questions, int groups, int papers,
                               String source, long importedAt) {
    }

    @GetMapping("/packs")
    public ApiResponse<List<PackResponse>> packs() {
        return ApiResponse.success(packMapper.selectList(new LambdaQueryWrapper<ContentPackRecord>()
                        .orderByAsc(ContentPackRecord::getPack))
                .stream()
                .map(record -> new PackResponse(record.getPack(), record.getTitle(), record.getVersion(),
                        record.getQuestionCount(), record.getGroupCount(), record.getPaperCount(),
                        record.getSource(),
                        record.getImportedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
                .toList());
    }
}

package com.yuka.learning.auth.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update — only non-null fields are applied. Blank ({@code ""})
 * clears the field back to null (display falls back to the username).
 */
public record UpdateProfileRequest(
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatar) {
}

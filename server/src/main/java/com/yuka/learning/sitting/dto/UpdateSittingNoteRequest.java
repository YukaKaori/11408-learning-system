package com.yuka.learning.sitting.dto;

import jakarta.validation.constraints.Size;

/** The 复盘 note alone — editable on every record, including one the app scored. */
public record UpdateSittingNoteRequest(@Size(max = 2000) String note) {
}

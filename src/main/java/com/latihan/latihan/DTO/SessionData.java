package com.latihan.latihan.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SessionData {
    @NotNull
    private String transcript;

    @NotNull
    private List<TimelineEntry> timeline;

    @NotNull
    private Boolean usePolished;
}

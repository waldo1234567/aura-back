package com.latihan.latihan.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id")
    private long userId;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "use_polished")
    private Boolean usePolished;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String timeline; // store the timeline list JSON

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "face_metrics", columnDefinition = "JSONB")
    private String faceMetrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hrv_metrics", columnDefinition = "JSONB")
    private String hrvMetrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "voice_metrics", columnDefinition = "JSONB")
    private String voiceMetrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_summary", columnDefinition = "JSONB")
    private String riskSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "flagged_excerpts", columnDefinition = "JSONB")
    private String flaggedExcerpts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actionable_highlights", columnDefinition = "JSONB")
    private String actionableHighlights;

    @Column(name = "detected_language")
    private String detectedLanguage;

    @Column(name = "translated_transcript", columnDefinition = "TEXT")
    private String translatedTranscript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "versions", columnDefinition = "JSONB")
    private String versions;

    @Column(name = "audio_url")
    private String audioUrl;

    @Column(name = "ai_reply", columnDefinition = "TEXT")
    private String aiReply;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spider_metrics", columnDefinition = "JSONB")
    private String spiderData;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;
}

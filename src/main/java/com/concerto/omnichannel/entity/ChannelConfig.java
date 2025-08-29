package com.concerto.omnichannel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "channel_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"channelId", "configKey"}))
public class ChannelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false, length = 50)
    private String channelId;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "config_type", length = 50)
    private String configType; // STRING, INTEGER, BOOLEAN, JSON

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_encrypted", nullable = false)
    private boolean encrypted = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    // Constructors
    public ChannelConfig() {}

    public ChannelConfig(String channelId, String configKey, String configValue, String configType) {
        this.channelId = channelId;
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getConfigType() { return configType; }
    public void setConfigType(String configType) { this.configType = configType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelConfig that = (ChannelConfig) o;
        return Objects.equals(channelId, that.channelId) && Objects.equals(configKey, that.configKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, configKey);
    }
}

package com.concerto.omnichannel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Generic API response wrapper")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Schema(description = "Indicates if the request was successful")
    private boolean success;

    @Schema(description = "Response data")
    private T data;

    @Schema(description = "Response message")
    private String message;

    @Schema(description = "Error details if request failed")
    private String error;

    @Schema(description = "Response timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "Correlation ID for request tracking")
    private String correlationId;

    @Schema(description = "API version")
    private String version = "v1";

    // Constructors
    public ApiResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiResponse(boolean success, T data, String message) {
        this();
        this.success = success;
        this.data = data;
        this.message = message;
    }

    // Static factory methods
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "Success");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    // Add this method to your ApiResponse class
    public static <T> ApiResponse<T> success(T data, String message, String correlationId) {
        ApiResponse<T> response = new ApiResponse<>(true, data, message);
        response.setCorrelationId(correlationId);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = message;
        return response;
    }

    public static <T> ApiResponse<T> error(String message, String error) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.message = message;
        response.error = error;
        return response;
    }

    // Builder pattern
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private final ApiResponse<T> response = new ApiResponse<>();

        public Builder<T> success(boolean success) {
            response.success = success;
            return this;
        }

        public Builder<T> data(T data) {
            response.data = data;
            return this;
        }

        public Builder<T> message(String message) {
            response.message = message;
            return this;
        }

        public Builder<T> error(String error) {
            response.error = error;
            return this;
        }

        public Builder<T> correlationId(String correlationId) {
            response.correlationId = correlationId;
            return this;
        }

        public Builder<T> timestamp(LocalDateTime timestamp) {
            response.timestamp = timestamp;
            return this;
        }

        public Builder<T> version(String version) {
            response.version = version;
            return this;
        }

        public ApiResponse<T> build() {
            if (response.timestamp == null) {
                response.timestamp = LocalDateTime.now();
            }
            return response;
        }
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}

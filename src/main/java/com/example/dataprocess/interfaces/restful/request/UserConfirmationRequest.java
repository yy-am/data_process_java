package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 用户确认结果请求。
 */
public record UserConfirmationRequest(
        @NotBlank String taskId,
        @NotBlank String templateCode,
        @NotEmpty List<FieldMappingDecisionDto> mappingDecisions,
        @NotEmpty List<OptionFieldDecisionDto> optionFieldDecisions,
        @NotEmpty List<InputFieldDecisionDto> inputFieldDecisions
) {
}

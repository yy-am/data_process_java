package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.OptionItem;
import com.example.dataprocess.domain.model.RequiredInputQuestion;
import com.example.dataprocess.domain.model.RequiredOptionQuestion;
import com.example.dataprocess.domain.model.SourceFieldCandidate;
import com.example.dataprocess.domain.model.TaskSession;
import com.example.dataprocess.domain.model.UnclearMappingQuestion;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 确认约束工具，提供确认问题所需的约束和候选信息。
 */
@Component
public class ConfirmationConstraintTool {

    public Map<String, Object> loadConfirmationConstraints(TaskSession session) {
        return Map.of(
                "requiredOptionField", "period",
                "requiredInputField", "D",
                "templateCode", session.templateRecognitionResult().templateCode(),
                "supportedOptionValues", List.of("2026-04", "2026-05", "2026-06")
        );
    }

    public UserConfirmationItems buildFallbackUserConfirmationItems(TaskSession session) {
        return new UserConfirmationItems(
                session.taskId(),
                session.templateRecognitionResult().templateCode(),
                List.of(
                        new UnclearMappingQuestion(
                                "A",
                                "Target Field A",
                                "Which source field should map to target field A?",
                                List.of(
                                        new SourceFieldCandidate("invoice_no", "invoice_no", 0.82),
                                        new SourceFieldCandidate("amount", "amount", 0.61)
                                )
                        )
                ),
                List.of(
                        new RequiredOptionQuestion(
                                "period",
                                "Period",
                                "Please choose a period.",
                                List.of(
                                        new OptionItem("2026-04", "2026-04"),
                                        new OptionItem("2026-05", "2026-05"),
                                        new OptionItem("2026-06", "2026-06")
                                )
                        )
                ),
                List.of(
                        new RequiredInputQuestion(
                                "D",
                                "Field D",
                                "Please input field D.",
                                "Example: manual-fill"
                        )
                )
        );
    }
}

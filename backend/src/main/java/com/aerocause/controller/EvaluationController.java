package com.aerocause.controller;

import com.aerocause.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
@Tag(name = "Evaluation", description = "Episode evaluation against expected outcomes")
@CrossOrigin(origins = "*")
public class EvaluationController {
    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/latest")
    @Operation(summary = "Get the latest persisted evaluation run")
    public Map<String, Object> getLatest() {
        return evaluationService.getLatest();
    }

    @PostMapping("/run")
    @Operation(summary = "Replay every episode and persist evaluation results")
    public ResponseEntity<Map<String, Object>> runEvaluation() {
        try {
            Map<String, Object> result = evaluationService.runAll();
            return ResponseEntity.status(Boolean.TRUE.equals(result.get("buildPass"))
                    ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY).body(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage() == null ? "Evaluation could not run." : e.getMessage()));
        }
    }
}

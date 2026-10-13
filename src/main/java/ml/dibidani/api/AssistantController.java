package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.AssistantDtos.ChatReply;
import ml.dibidani.api.dto.AssistantDtos.ChatRequest;
import ml.dibidani.service.AssistantService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/assistant")
public class AssistantController {
  private final AssistantService assistant;

  public AssistantController(AssistantService assistant) { this.assistant = assistant; }

  @PostMapping("/chat")
  public ChatReply chat(@Valid @RequestBody ChatRequest r) { return assistant.chat(r); }
}

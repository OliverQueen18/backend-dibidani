package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.UserDtos.*;
import ml.dibidani.domain.Role;
import ml.dibidani.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/couriers")
@PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
public class AdminCourierController {
  private final UserService users;
  public AdminCourierController(UserService users) { this.users = users; }

  @GetMapping("/manage")
  public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
    return users.page(q, Role.COURIER, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateCourierRequest r) { return users.createCourier(r); }

  @PutMapping("/{id}")
  public UserResponse update(Authentication actor, @PathVariable Long id, @Valid @RequestBody UpdateCourierRequest r) {
    return users.updateCourier(id, r, actor.getName());
  }
}

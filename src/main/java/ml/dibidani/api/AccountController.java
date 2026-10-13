package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.AccountDtos.*;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
  private final AccountService account;

  public AccountController(AccountService account) { this.account = account; }

  @GetMapping("/dashboard")
  public DashboardResponse dashboard(Authentication a) { return account.dashboard(a.getName()); }

  @GetMapping("/profile")
  public ProfileResponse profile(Authentication a) { return account.profile(a.getName()); }

  @PutMapping("/profile")
  public ProfileResponse updateProfile(Authentication a, @Valid @RequestBody ProfileRequest r) { return account.updateProfile(a.getName(), r); }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(Authentication a, @Valid @RequestBody PasswordRequest r) { account.changePassword(a.getName(), r); }

  @GetMapping("/addresses")
  public List<AddressResponse> addresses(Authentication a) { return account.addresses(a.getName()); }

  @PostMapping("/addresses")
  @ResponseStatus(HttpStatus.CREATED)
  public AddressResponse createAddress(Authentication a, @Valid @RequestBody AddressRequest r) { return account.createAddress(a.getName(), r); }

  @PutMapping("/addresses/{id}")
  public AddressResponse updateAddress(Authentication a, @PathVariable Long id, @Valid @RequestBody AddressRequest r) { return account.updateAddress(a.getName(), id, r); }

  @PostMapping("/addresses/{id}/default")
  public AddressResponse setDefault(Authentication a, @PathVariable Long id) { return account.setDefault(a.getName(), id); }

  @DeleteMapping("/addresses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAddress(Authentication a, @PathVariable Long id) { account.deleteAddress(a.getName(), id); }

  @GetMapping("/favorites")
  public List<ProductSummary> favorites(Authentication a) { return account.favorites(a.getName()); }

  @GetMapping("/favorites/ids")
  public List<Long> favoriteIds(Authentication a) { return account.favoriteIds(a.getName()); }

  @PutMapping("/favorites/{productId}")
  public List<Long> addFavorite(Authentication a, @PathVariable Long productId) { return account.addFavorite(a.getName(), productId); }

  @DeleteMapping("/favorites/{productId}")
  public List<Long> removeFavorite(Authentication a, @PathVariable Long productId) { return account.removeFavorite(a.getName(), productId); }

  @PostMapping("/favorites/merge")
  public List<Long> mergeFavorites(Authentication a, @Valid @RequestBody FavoritesMergeRequest r) { return account.mergeFavorites(a.getName(), r.productIds()); }
}

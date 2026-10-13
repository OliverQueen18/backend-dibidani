package ml.dibidani.service;

import ml.dibidani.api.dto.AccountDtos.*;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class AccountService {
  private static final int MAX_ADDRESSES = 20;
  private static final int MAX_FAVORITES = 500;

  private final UserService users;
  private final AppUserRepository userRepo;
  private final CustomerAddressRepository addresses;
  private final FavoriteRepository favorites;
  private final ProductRepository products;
  private final CustomerOrderRepository orders;
  private final DeliveryService delivery;
  private final OrderMapper mapper;
  private final PasswordEncoder encoder;

  public AccountService(UserService users, AppUserRepository userRepo, CustomerAddressRepository addresses, FavoriteRepository favorites,
                        ProductRepository products, CustomerOrderRepository orders, DeliveryService delivery, OrderMapper mapper, PasswordEncoder encoder) {
    this.users = users;
    this.userRepo = userRepo;
    this.addresses = addresses;
    this.favorites = favorites;
    this.products = products;
    this.orders = orders;
    this.delivery = delivery;
    this.mapper = mapper;
    this.encoder = encoder;
  }

  // ---------- Profil et sécurité ----------

  @Transactional(readOnly = true)
  public ProfileResponse profile(String email) { return ProfileResponse.of(users.byEmail(email)); }

  public ProfileResponse updateProfile(String email, ProfileRequest r) {
    var u = users.byEmail(email);
    u.setFullName(r.fullName().trim());
    u.setPhone(r.phone().trim());
    return ProfileResponse.of(u);
  }

  public void changePassword(String email, PasswordRequest r) {
    var u = users.byEmail(email);
    if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) throw new IllegalArgumentException("Mot de passe actuel incorrect");
    if (encoder.matches(r.newPassword(), u.getPasswordHash())) throw new IllegalArgumentException("Le nouveau mot de passe doit être différent de l'actuel");
    u.setPasswordHash(encoder.encode(r.newPassword()));
    u.revokeTokens();
    userRepo.save(u);
  }

  // ---------- Adresses ----------

  @Transactional(readOnly = true)
  public List<AddressResponse> addresses(String email) {
    return addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(users.byEmail(email).getId()).stream().map(AddressResponse::of).toList();
  }

  public AddressResponse createAddress(String email, AddressRequest r) {
    var u = users.byEmail(email);
    if (addresses.countByUserId(u.getId()) >= MAX_ADDRESSES) throw new IllegalArgumentException(MAX_ADDRESSES + " adresses maximum");
    var a = new CustomerAddress();
    a.setUser(u);
    apply(a, r);
    boolean first = addresses.countByUserId(u.getId()) == 0;
    a.setDefaultAddress(false);
    a = addresses.save(a);
    if (first || Boolean.TRUE.equals(r.defaultAddress())) makeDefault(u.getId(), a);
    return AddressResponse.of(a);
  }

  public AddressResponse updateAddress(String email, Long id, AddressRequest r) {
    var u = users.byEmail(email);
    var a = addresses.findByIdAndUserId(id, u.getId()).orElseThrow(() -> new NotFoundException("Adresse introuvable"));
    apply(a, r);
    if (Boolean.TRUE.equals(r.defaultAddress())) makeDefault(u.getId(), a);
    return AddressResponse.of(a);
  }

  public AddressResponse setDefault(String email, Long id) {
    var u = users.byEmail(email);
    var a = addresses.findByIdAndUserId(id, u.getId()).orElseThrow(() -> new NotFoundException("Adresse introuvable"));
    makeDefault(u.getId(), a);
    return AddressResponse.of(a);
  }

  public void deleteAddress(String email, Long id) {
    var u = users.byEmail(email);
    var a = addresses.findByIdAndUserId(id, u.getId()).orElseThrow(() -> new NotFoundException("Adresse introuvable"));
    boolean wasDefault = a.isDefaultAddress();
    addresses.delete(a);
    addresses.flush();
    if (wasDefault) addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(u.getId()).stream().findFirst().ifPresent(n -> n.setDefaultAddress(true));
  }

  private void apply(CustomerAddress a, AddressRequest r) {
    a.setLabel(Slugs.blankToNull(r.label()));
    a.setFullName(r.fullName().trim());
    a.setPhone(r.phone().trim());
    a.setAddressLine(r.addressLine().trim());
    a.setDeliveryZone(delivery.activeZone(r.deliveryZoneId()));
    a.setLandmark(Slugs.blankToNull(r.landmark()));
  }

  private void makeDefault(Long userId, CustomerAddress target) {
    addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(userId).forEach(x -> x.setDefaultAddress(x.getId().equals(target.getId())));
  }

  // ---------- Favoris ----------

  @Transactional(readOnly = true)
  public List<ProductSummary> favorites(String email) {
    return favorites.findByUserIdOrderByCreatedAtDesc(users.byEmail(email).getId()).stream()
        .map(Favorite::getProduct).filter(Product::isActive).map(ProductSummary::of).toList();
  }

  @Transactional(readOnly = true)
  public List<Long> favoriteIds(String email) {
    return favorites.findByUserIdOrderByCreatedAtDesc(users.byEmail(email).getId()).stream().map(f -> f.getProduct().getId()).toList();
  }

  public List<Long> addFavorite(String email, Long productId) {
    var u = users.byEmail(email);
    if (!favorites.existsByUserIdAndProductId(u.getId(), productId)) {
      if (favorites.countByUserId(u.getId()) >= MAX_FAVORITES) throw new IllegalArgumentException("Liste de favoris pleine");
      var p = products.findByIdAndActiveTrue(productId).orElseThrow(() -> new NotFoundException("Produit introuvable"));
      var f = new Favorite();
      f.setUser(u);
      f.setProduct(p);
      favorites.save(f);
    }
    return favoriteIds(email);
  }

  public List<Long> removeFavorite(String email, Long productId) {
    var u = users.byEmail(email);
    favorites.findByUserIdAndProductId(u.getId(), productId).ifPresent(favorites::delete);
    favorites.flush();
    return favoriteIds(email);
  }

  /** Favoris du visiteur ajoutés à son compte à la connexion (produits inconnus ou inactifs ignorés). */
  public List<Long> mergeFavorites(String email, List<Long> productIds) {
    var u = users.byEmail(email);
    if (productIds != null) {
      for (Long id : new LinkedHashSet<>(productIds)) {
        if (id == null || favorites.existsByUserIdAndProductId(u.getId(), id)) continue;
        if (favorites.countByUserId(u.getId()) >= MAX_FAVORITES) break;
        products.findByIdAndActiveTrue(id).ifPresent(p -> {
          var f = new Favorite();
          f.setUser(u);
          f.setProduct(p);
          favorites.save(f);
        });
      }
    }
    favorites.flush();
    return favoriteIds(email);
  }

  // ---------- Tableau de bord ----------

  @Transactional(readOnly = true)
  public DashboardResponse dashboard(String email) {
    var u = users.byEmail(email);
    var all = orders.findByCustomerIdOrderByCreatedAtDesc(u.getId());
    var recent = all.stream().limit(5).map(mapper::summary).toList();
    var current = all.stream().filter(o -> o.getStatus().isOpen()).findFirst().map(mapper::summary).orElse(null);
    BigDecimal spent = all.stream().filter(o -> o.getStatus().countsAsSale()).map(CustomerOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    var favs = favorites.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 4)).stream()
        .map(Favorite::getProduct).filter(Product::isActive).map(ProductSummary::of).toList();
    var shortcuts = List.of(
        new Shortcut("Suivre une commande", "truck", "/compte/suivi"),
        new Shortcut("Mes adresses", "map-pin", "/compte/adresses"),
        new Shortcut("Trouver ma pièce", "wrench", "/trouver-ma-piece"),
        new Shortcut("Contacter le support", "message", "/contact"));
    return new DashboardResponse(u.getFullName(), all.size(), spent, favorites.countByUserId(u.getId()), addresses.countByUserId(u.getId()),
        current, recent, favs, recommendations(u.getId(), 8), shortcuts);
  }

  /** Produits des catégories déjà achetées, non encore commandés ; à défaut, meilleures ventes. */
  private List<ProductSummary> recommendations(Long userId, int limit) {
    Set<Long> bought = new HashSet<>(orders.purchasedProductIds(userId));
    List<Product> out = new ArrayList<>();
    for (Long categoryId : orders.purchasedCategoryIds(userId)) {
      for (Product p : products.findForCategory(categoryId, PageRequest.of(0, limit))) {
        if (out.size() >= limit) break;
        if (!bought.contains(p.getId()) && out.stream().noneMatch(x -> x.getId().equals(p.getId())) && p.getStock() > 0) out.add(p);
      }
    }
    if (out.size() < limit) {
      for (Product p : products.findByBestSellerTrueAndActiveTrueOrderByCreatedAtDesc(PageRequest.of(0, limit * 2))) {
        if (out.size() >= limit) break;
        if (!bought.contains(p.getId()) && out.stream().noneMatch(x -> x.getId().equals(p.getId()))) out.add(p);
      }
    }
    return out.stream().map(ProductSummary::of).toList();
  }
}

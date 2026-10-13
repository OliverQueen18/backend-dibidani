package ml.dibidani.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.Unit;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.UnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UnitService {
  private final UnitRepository units;
  private final ProductRepository products;

  public UnitService(UnitRepository units, ProductRepository products) {
    this.units = units;
    this.products = products;
  }

  public record UnitRequest(@NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String name, @Size(max = 30) String symbol,
                            Boolean active, Integer displayOrder) {}

  public record UnitResponse(Long id, String name, String symbol, boolean active, int displayOrder, boolean used) {}

  public List<UnitResponse> list(boolean activeOnly) {
    var list = activeOnly ? units.findByActiveTrueOrderByDisplayOrderAscNameAsc() : units.findAllByOrderByDisplayOrderAscNameAsc();
    return list.stream().map(this::view).toList();
  }

  @Transactional
  public UnitResponse save(Long id, UnitRequest r) {
    Unit u = id == null ? new Unit() : units.findById(id).orElseThrow(() -> new NotFoundException("Unité introuvable"));
    String name = r.name().trim();
    if (id == null ? units.existsByNameIgnoreCase(name) : units.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Cette unité existe déjà");
    if (id != null && !u.getName().equalsIgnoreCase(name) && products.existsByUnitIgnoreCase(u.getName())) {
      throw new ConflictException("Unité utilisée par des produits : créez-en une nouvelle plutôt que de la renommer");
    }
    u.setName(name);
    u.setSymbol(Slugs.blankToNull(r.symbol()));
    u.setActive(r.active() == null || r.active());
    u.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
    return view(units.save(u));
  }

  @Transactional
  public UnitResponse deactivate(Long id) {
    Unit u = units.findById(id).orElseThrow(() -> new NotFoundException("Unité introuvable"));
    u.setActive(false);
    return view(u);
  }

  private UnitResponse view(Unit u) {
    return new UnitResponse(u.getId(), u.getName(), u.getSymbol(), u.isActive(), u.getDisplayOrder(), products.existsByUnitIgnoreCase(u.getName()));
  }
}

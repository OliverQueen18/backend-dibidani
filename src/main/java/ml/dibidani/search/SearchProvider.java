package ml.dibidani.search;

import ml.dibidani.api.dto.SearchDtos.SuggestionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Point d'extension du moteur de recherche. L'implémentation PostgreSQL est active par défaut
 * ({@code app.search.provider=postgres}) ; une implémentation OpenSearch pourra être ajoutée
 * sans toucher aux contrôleurs ni au frontend.
 */
public interface SearchProvider {

  /** Identifiants des produits correspondants, dans l'ordre de pertinence ou de tri demandé. */
  Page<Long> searchProductIds(ProductFilter filter, Pageable pageable);

  SuggestionResponse suggest(String query, int limit);
}

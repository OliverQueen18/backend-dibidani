package ml.dibidani.api;

import ml.dibidani.api.dto.SearchDtos.SearchResponse;
import ml.dibidani.api.dto.SearchDtos.SuggestionResponse;
import ml.dibidani.search.SearchProvider;
import ml.dibidani.service.ProductService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
  private final SearchProvider search;
  private final ProductService products;

  public SearchController(SearchProvider search, ProductService products) {
    this.search = search;
    this.products = products;
  }

  /** Résultats paginés + catégories, marques et vendeurs liés à la requête. */
  @GetMapping
  public SearchResponse search(@ModelAttribute ProductQuery query) {
    String q = query.q() == null ? "" : query.q().trim();
    return new SearchResponse(q, products.page(query.toFilter(false), query.pageOrDefault(), query.sizeOrDefault()), search.suggest(q, 6));
  }

  /** Autocomplétion légère (à partir de 2 caractères). */
  @GetMapping("/suggestions")
  public SuggestionResponse suggestions(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "6") int limit) {
    return search.suggest(q, limit);
  }
}

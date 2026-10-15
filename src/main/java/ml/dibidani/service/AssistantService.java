package ml.dibidani.service;

import ml.dibidani.api.dto.AssistantDtos.*;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.domain.Product;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.search.ProductFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class AssistantService {
  private static final List<String> HELLO = List.of(
      "bonjour", "salut", "hello", "bonsoir", "hey", "coucou", "i ni ce", "i ni sogoma", "i ni wula");
  private static final List<String> STOP = List.of(
      "je", "j", "tu", "il", "ils", "elle", "elles", "on", "nous", "vous", "un", "une", "des", "le", "la",
      "les", "de", "du", "d", "au", "aux", "en", "et", "ou", "mon", "ma", "mes", "moi", "me", "y", "a",
      "pour", "avec", "sans", "sur", "dans", "par", "pas", "plus", "tres", "svp", "stp", "sil", "s", "te",
      "plait", "voudrais", "veux", "voulez", "peux", "peut", "pouvez", "chercher", "cherche", "rechercher",
      "recherche", "trouver", "trouve", "montre", "montrer", "donner", "donne", "besoin", "produit",
      "produits", "article", "articles", "sest", "est", "es", "suis", "sont", "sommes", "etes", "avez",
      "avons", "ont", "ai", "as", "ce", "cet", "cette", "ces", "que", "qui", "quoi", "dont", "quand", "si",
      "ici", "aussi", "alors", "donc", "voila", "euh", "hum", "ben", "bah", "hein", "oui", "non", "merci",
      "bonjour", "salut", "hello", "bonsoir", "dibidani", "assistant", "cela", "ca", "votre", "vos", "notre",
      "nos", "leur", "leurs", "avoir", "etre", "fait", "faire", "dis", "dites", "voir", "aimerais");
  private static final Pattern ORDINAL = Pattern.compile("\\b(premier|1er|1ere|deuxieme|2e|second|troisieme|3e|quatrieme|4e)\\b");
  private static final Pattern QTY = Pattern.compile("\\b(\\d{1,3})\\b");

  private final ProductService products;
  private final ProductRepository productRepo;

  public AssistantService(ProductService products, ProductRepository productRepo) {
    this.products = products;
    this.productRepo = productRepo;
  }

  public ChatReply chat(ChatRequest r) {
    String raw = r.message() == null ? "" : r.message().trim();
    String n = fold(raw);
    int cart = r.cartCount() == null ? 0 : Math.max(0, r.cartCount());
    List<ProductSummary> last = loadLast(r.lastProductIds());

    if (isHello(n) && productHint(n).isBlank()) return greeting();
    if (isHelp(n)) return help();
    if (isTrack(n)) return track();
    if (isAdd(n)) return add(n, last, cart);
    if (isCheckout(n) && productHint(n).isBlank()) return checkout(cart);
    if (isCart(n) && !isAdd(n)) return cart(cart);

    String q = productHint(n);
    if (q.isBlank() && isCheckout(n)) return checkout(cart);
    if (q.isBlank()) return greeting();
    return search(q);
  }

  private ChatReply greeting() {
    List<ProductSummary> popular = products.featured(4);
    if (popular.isEmpty()) popular = products.bestSellers(4);
    return new ChatReply(
        "Bonjour, je suis l'assistant Dibidani. Dites-moi un produit à l'écrit ou au micro : je cherche, j'ajoute au panier et je vous emmène jusqu'à la commande.",
        "GREETING", "", popular, chips(), null);
  }

  private ChatReply help() {
    return new ChatReply(
        "Je peux chercher un produit (écrit ou vocal), l'ajouter au panier et vous conduire à la commande. Après votre commande, Dibidani vérifie la disponibilité auprès des vendeurs puis vous confirme. Livraison à Bamako à partir de 2 500 FCFA.",
        "HELP", "", List.of(), chips(), null);
  }

  private ChatReply track() {
    return new ChatReply("Je vous ouvre le suivi de commande. Indiquez votre numéro DBD-…", "TRACK", "", List.of(),
        List.of("Chercher un produit", "Voir le panier"), new ChatAction("NAVIGATE", null, null, "/track"));
  }

  private ChatReply cart(int count) {
    if (count <= 0) {
      return new ChatReply("Votre panier est vide. Quel produit cherchez-vous ?", "CART", "", List.of(), chips(), null);
    }
    return new ChatReply("Vous avez " + count + " article(s) dans le panier. Je vous l'ouvre.", "CART", "", List.of(),
        List.of("Passer commande", "Continuer mes achats"), new ChatAction("NAVIGATE", null, null, "/cart"));
  }

  private ChatReply checkout(int count) {
    if (count <= 0) {
      return new ChatReply("Votre panier est vide. Dites-moi ce que vous voulez commander, je le cherche.", "CHECKOUT", "",
          List.of(), chips(), null);
    }
    return new ChatReply("Parfait, je vous emmène à l'étape de commande (livraison et paiement).", "CHECKOUT", "", List.of(),
        List.of("Voir le panier"), new ChatAction("NAVIGATE", null, null, "/checkout"));
  }

  private ChatReply add(String n, List<ProductSummary> last, int cart) {
    int qty = quantity(n);
    ProductSummary pick = pick(n, last);
    if (pick == null) {
      String q = productHint(n);
      if (!q.isBlank()) {
        ChatReply found = search(q);
        if (!found.products().isEmpty()) {
          pick = found.products().getFirst();
          return new ChatReply("J'ajoute « " + pick.name() + " » au panier. Vous pouvez continuer ou passer commande.",
              "ADD_CART", q, found.products(), List.of("Passer commande", "Voir le panier", "Chercher autre chose"),
              new ChatAction("ADD_TO_CART", pick.id(), qty, null));
        }
      }
      return new ChatReply("Quel produit dois-je ajouter ? Dites son nom, ou « ajoute le premier » après une recherche.",
          "ADD_CART", "", last, chips(), null);
    }
    int next = cart + qty;
    return new ChatReply("« " + pick.name() + " » est dans le panier (" + next + " article(s)). Dites « commande » quand vous êtes prêt.",
        "ADD_CART", "", last.isEmpty() ? List.of(pick) : last,
        List.of("Passer commande", "Voir le panier", "Chercher autre chose"),
        new ChatAction("ADD_TO_CART", pick.id(), qty, null));
  }

  private ChatReply search(String q) {
    List<ProductSummary> list = products.page(ProductFilter.query(q), 0, 6).content();
    if (list.isEmpty()) {
      String focus = longestTerm(q);
      if (!focus.isBlank() && !focus.equals(q)) list = products.page(ProductFilter.query(focus), 0, 6).content();
    }
    if (list.isEmpty()) {
      return new ChatReply("Je n'ai pas trouvé « " + q + " ». Essayez un autre mot (marque, pièce, aliment…) ou une catégorie.",
          "SEARCH", q, List.of(), List.of("Quincaillerie", "Pièces détachées", "Électronique", "Alimentation"), null);
    }
    return new ChatReply("Voici ce que j'ai pour « " + q + " ». Dites « ajoute le premier » ou touchez un produit, puis « commande ».",
        "SEARCH", q, list, List.of("Ajoute le premier", "Voir le panier", "Passer commande"), null);
  }

  private static String longestTerm(String q) {
    String best = "";
    for (String w : q.split("\\s+")) {
      if (w.length() > best.length()) best = w;
    }
    return best.length() >= 3 ? best : "";
  }

  private List<ProductSummary> loadLast(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    var byId = productRepo.findAllById(ids).stream().filter(Product::isActive)
        .collect(java.util.stream.Collectors.toMap(Product::getId, ProductSummary::of, (a, b) -> a));
    List<ProductSummary> out = new ArrayList<>();
    for (Long id : ids) {
      var p = byId.get(id);
      if (p != null) out.add(p);
    }
    return out;
  }

  private static ProductSummary pick(String n, List<ProductSummary> last) {
    if (last.isEmpty()) return null;
    Matcher ord = ORDINAL.matcher(n);
    if (ord.find()) {
      int i = switch (ord.group()) {
        case "premier", "1er", "1ere" -> 0;
        case "deuxieme", "2e", "second" -> 1;
        case "troisieme", "3e" -> 2;
        default -> 3;
      };
      return i < last.size() ? last.get(i) : last.getFirst();
    }
    if (n.contains("celui") || n.contains("celle") || n.contains("ca") || n.contains("premier resultat")) return last.getFirst();
    String hint = productHint(n);
    if (!hint.isBlank()) {
      for (ProductSummary p : last) {
        if (fold(p.name()).contains(hint) || hint.contains(fold(p.name()))) return p;
        for (String w : hint.split("\\s+")) {
          if (w.length() >= 4 && fold(p.name()).contains(w)) return p;
        }
      }
    }
    return last.size() == 1 ? last.getFirst() : null;
  }

  private static int quantity(String n) {
    if (n.matches(".*\\b(deux|2)\\b.*")) return 2;
    if (n.matches(".*\\b(trois|3)\\b.*")) return 3;
    if (n.matches(".*\\b(quatre|4)\\b.*")) return 4;
    if (n.matches(".*\\b(cinq|5)\\b.*")) return 5;
    Matcher m = QTY.matcher(n);
    if (m.find()) {
      try {
        int v = Integer.parseInt(m.group(1));
        if (v >= 1 && v <= 99) return v;
      } catch (NumberFormatException ignored) {}
    }
    return 1;
  }

  private static boolean isHello(String n) { return HELLO.stream().anyMatch(n::contains); }
  private static boolean isHelp(String n) {
    return n.contains("aide") || n.contains("comment") || n.contains("livraison") || n.contains("horaire") || n.contains("bonjour dibidani");
  }
  private static boolean isTrack(String n) { return n.contains("suivi") || n.contains("suivre") || n.contains("tracking") || n.contains("ou est ma commande"); }
  private static boolean isAdd(String n) {
    return n.contains("ajout") || n.contains("mets dans") || n.contains("met dans") || n.contains("je prends")
        || n.contains("je prend") || n.contains("prendre") || n.contains("au panier");
  }
  private static boolean isCart(String n) { return n.contains("panier") && !n.contains("ajout"); }
  private static boolean isCheckout(String n) {
    return n.contains("command") || n.contains("checkout") || n.contains("payer") || n.contains("paiement")
        || n.contains("valider") && n.contains("achat");
  }

  private static String productHint(String n) {
    String s = n;
    s = s.replaceAll("\\b(ajoute|ajouter|ajout|mets|met|prendre|prends|prend)\\b", " ");
    s = s.replaceAll("\\b(le premier|la premiere|au panier|panier|commande|commander|passer|payer)\\b", " ");
    s = s.replaceAll("\\b(je cherche|cherche moi|recherche|je veux|je voudrais|montre moi|trouve moi|besoin de)\\b", " ");
    StringBuilder out = new StringBuilder();
    for (String w : s.split("[^a-z0-9+]+")) {
      if (w.isBlank() || STOP.contains(w) || (w.length() < 3 && !w.chars().allMatch(Character::isDigit))) continue;
      if (out.length() > 0) out.append(' ');
      out.append(w);
    }
    return out.toString().trim();
  }

  private static List<String> chips() {
    return List.of("Je cherche une perceuse", "Riz 50 kg", "Voir le panier", "Passer commande");
  }

  private static String fold(String s) {
    if (s == null) return "";
    String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
    return n.replaceAll("\\p{M}+", "").replace("'", " ").replace("’", " ").replaceAll("\\s+", " ").trim();
  }
}

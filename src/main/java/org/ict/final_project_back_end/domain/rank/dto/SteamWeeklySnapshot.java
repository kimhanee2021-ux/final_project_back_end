package org.ict.final_project_back_end.domain.rank.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validated, immutable original TOP 10. Never remove products or renumber ranks. */
public record SteamWeeklySnapshot(String country, LocalDate week, Instant fetchedAt, List<Product> products) {
  public SteamWeeklySnapshot {
    country = normalizeCountry(country);
    if (week == null || week.getDayOfWeek() != DayOfWeek.TUESDAY || fetchedAt == null) {
      throw new IllegalArgumentException("A Tuesday week and fetch timestamp are required");
    }
    products = List.copyOf(products);
    if (products.size() != 10) throw new IllegalArgumentException("Exactly ten products are required");
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < products.size(); i++) {
      Product product = products.get(i);
      if (product.rank() != i + 1 || !ids.add(product.appId())) {
        throw new IllegalArgumentException("TOP 10 must contain ranks 1 through 10 and unique App IDs");
      }
    }
  }

  public static String normalizeCountry(String country) {
    String code = country == null ? "" : country.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("KR", "US").contains(code)) throw new IllegalArgumentException("Only KR and US are supported");
    return code;
  }

  public String status() {
    return products.stream().anyMatch(p -> p.imageUrl() == null
        || p.consecutiveWeeks() == null || "UNKNOWN".equals(p.changeKind())) ? "VERIFIED_GAPS" : "VERIFIED";
  }

  public String sourceUrl() {
    return "https://store.steampowered.com/charts/topsellers/" + country + "/" + week;
  }

  public record Product(int rank, long appId, String name, Integer previousRank,
                        Integer consecutiveWeeks, String imageUrl, Boolean firstTop100) {
    /** Legacy callers have no first-entry evidence. */
    public Product(int rank, long appId, String name, Integer previousRank,
                   Integer consecutiveWeeks, String imageUrl) {
      this(rank, appId, name, previousRank, consecutiveWeeks, imageUrl, null);
    }
    public Product {
      if (rank < 1 || rank > 10 || appId <= 0 || name == null || name.isBlank() || name.length() > 500) {
        throw new IllegalArgumentException("Invalid product identity, name or rank");
      }
      if ((previousRank != null && previousRank < 0) || (consecutiveWeeks != null && consecutiveWeeks < 0)) {
        throw new IllegalArgumentException("Negative ranking history is invalid");
      }
      if (imageUrl != null && !imageUrl.startsWith("https://")) throw new IllegalArgumentException("Image URL must use HTTPS");
    }

    public String changeKind() {
      // Match Steam chart: a previous TOP 100 rank takes priority over first_top100.
      if (previousRank == null || previousRank == 0 || previousRank > 100) {
        return firstTop100 == null ? "UNKNOWN" : firstTop100 ? "NEW" : "RETURNING";
      }
      return previousRank > rank ? "UP" : previousRank < rank ? "DOWN" : "SAME";
    }
  }
}

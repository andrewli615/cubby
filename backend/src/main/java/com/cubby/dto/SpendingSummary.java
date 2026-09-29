package com.cubby.dto;

import java.math.BigDecimal;
import java.util.List;

/** Sankey-ready company spending totals, grouped independently by currency. */
public record SpendingSummary(List<CurrencyFlow> currencies) {
    public SpendingSummary {
        currencies = List.copyOf(currencies);
    }

    public record CurrencyFlow(String currency, BigDecimal total, List<Node> nodes, List<Link> links) {
        public CurrencyFlow {
            nodes = List.copyOf(nodes);
            links = List.copyOf(links);
        }
    }

    public record Node(String id, String label) {}
    public record Link(String source, String target, BigDecimal value) {}
}

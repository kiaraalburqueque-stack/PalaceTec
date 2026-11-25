package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Optional;

/** Montos como los lee Decimal(str(valor)) de Python en la billetera de Django. */
public final class Amounts {

    private static final BigDecimal LIMIT = new BigDecimal("1e16");
    /** Un texto más largo no puede ser un monto válido y encarecería la conversión. */
    private static final int MAX_TEXT = 100;

    private Amounts() {
    }

    /** Monto positivo, menor a 1e16 y con hasta dos decimales; vacío si no lo es. */
    public static Optional<BigDecimal> positive(JsonNode node) {
        if (node == null || !node.isTextual() && !node.isNumber()) {
            return Optional.empty();
        }
        String text = RequestFields.strip(node.asText()).replace("_", "");
        if (text.length() > MAX_TEXT) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(text);
            // El rango se revisa antes que los decimales: así un exponente enorme no se expande.
            boolean valid = amount.signum() > 0 && amount.compareTo(LIMIT) < 0
                    && amount.stripTrailingZeros().scale() <= 2;
            return valid ? Optional.of(amount) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}

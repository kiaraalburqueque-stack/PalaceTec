package com.tecsup.palacetec.realtime;

import com.tecsup.palacetec.dto.WalletSummaryResponse;

/** Spring movió el saldo: wallet:update sale al confirmarse la transacción, sin esperar al sondeo. */
public record WalletChanged(Long userId, WalletSummaryResponse wallet) {
}

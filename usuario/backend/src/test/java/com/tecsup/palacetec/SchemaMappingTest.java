package com.tecsup.palacetec;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** Que el contexto arranque ya prueba que Hibernate validó las entidades contra el esquema migrado por Django. */
class SchemaMappingTest extends ApiTest {

    @Autowired
    private EntityManagerFactory factory;
    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void hibernateOnlyValidatesTheSchema() {
        assertThat(ddlAuto).isEqualTo("validate");
    }

    @Test
    void entitiesUseTheDjangoTables() {
        var tables = factory.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getAnnotation(Table.class).name())
                .collect(Collectors.toSet());
        assertThat(tables).containsExactlyInAnyOrder("users", "accounts_authsession", "wallets", "wallet_transactions",
                "wallet_balances", "roulette_game", "rooms", "game_rounds", "user_bets", "responsible_dailylimit",
                "responsible_dailyspending", "responsible_excludedperson", "favorite_games", "room_connections",
                "casino_play", "notifications", "notification_settings", "login_history");
    }

    @Test
    void walletBalancesIsStillAView() {
        assertThat(jdbc.queryForObject("select table_type from information_schema.tables "
                + "where table_schema = 'public' and table_name = 'wallet_balances'", String.class)).isEqualTo("VIEW");
    }
}

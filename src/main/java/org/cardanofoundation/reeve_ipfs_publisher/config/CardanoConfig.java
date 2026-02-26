package org.cardanofoundation.reeve_ipfs_publisher.config;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.DefaultUtxoSupplier;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class CardanoConfig {

    @Bean
    public Network network() {
        // For demo purposes, we use testnet. Change as needed.
        return Networks.testnet();
    }

    @Bean
    public BackendService backendService(@Value("${reeve.publisher.url}") String apiUrl,
                                         @Value("${reeve.publisher.project-id}") String projectId) {
        return new BFBackendService(apiUrl, projectId);
    }

    @Bean
    public UtxoSupplier utxoSupplier(BackendService backendService) {
        return new DefaultUtxoSupplier(backendService.getUtxoService());
    }

    @Bean
    public QuickTxBuilder quickTxBuilder(BackendService backendService) {
        return new QuickTxBuilder(backendService);
    }

    @Bean
    public Account account(@Value("${reeve.wallet.mnemonic}") String mnemonic, Network network) {
        return Account.createFromMnemonic(network, mnemonic);
    }

}

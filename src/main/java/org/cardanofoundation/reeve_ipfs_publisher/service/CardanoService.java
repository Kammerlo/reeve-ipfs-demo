package org.cardanofoundation.reeve_ipfs_publisher.service;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cardanofoundation.reeve_ipfs_publisher.config.ReeveProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardanoService {

    private final ReeveProperties reeveProperties;
    private final Account account;
    private final QuickTxBuilder quickTxBuilder;


    public void publishHashes(List<String> ipfsHashes, List<String> arweaveHashes) {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("org", createOrganisationMetadataMap());
        if(!ipfsHashes.isEmpty()) {
            MetadataList ipfsHashesList = MetadataBuilder.createList();
            ipfsHashes.stream().forEach(ipfsHashesList::add);
            map.put("ipfs", ipfsHashesList);
        }
        if(!arweaveHashes.isEmpty()) {
            MetadataList arweaveHashesList = MetadataBuilder.createList();
            arweaveHashes.stream().forEach(arweaveHashesList::add);
            map.put("arweave", arweaveHashesList);
        }
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(1447, map);

        Tx tx = new Tx()
                .payToAddress(account.baseAddress(), Amount.ada(2))
                .from(account.baseAddress())
                .attachMetadata(metadata);
        TxResult txResult = quickTxBuilder.compose(tx)
                .feePayer(account.baseAddress())
                .withSigner(SignerProviders.signerFrom(account))
                .completeAndWait();
        log.info("Tx result: {}", txResult);
    }

    private MetadataMap createOrganisationMetadataMap() {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("id", reeveProperties.getOrganisation().getId());
        map.put("name", reeveProperties.getOrganisation().getName());
        map.put("currncy_id", reeveProperties.getOrganisation().getCurrencyId());
        map.put("country_code", reeveProperties.getOrganisation().getCountryCode());
        map.put("tax_id_number", reeveProperties.getOrganisation().getTaxIdNumber());
        return map;
    }
}

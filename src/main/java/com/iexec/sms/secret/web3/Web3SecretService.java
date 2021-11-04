/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.sms.secret.web3;


import com.iexec.sms.encryption.EncryptionService;
import com.iexec.sms.secret.AbstractSecretService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class Web3SecretService extends AbstractSecretService {

    private Web3SecretRepository web3SecretRepository;

    public Web3SecretService(Web3SecretRepository web3SecretRepository,
                             EncryptionService encryptionService) {
        super(encryptionService);
        this.web3SecretRepository = web3SecretRepository;
    }

    public Optional<Web3Secret> getSecret(String secretAddress, boolean shouldDecryptValue) {
        secretAddress = secretAddress.toLowerCase();
        Optional<Web3Secret> secret = web3SecretRepository.findWeb3SecretByAddress(secretAddress);
        if (secret.isEmpty()) {
            return Optional.empty();
        }
        if (shouldDecryptValue) {
            decryptSecret(secret.get());
        }
        return Optional.of(secret.get());
    }

    public Optional<Web3Secret> getSecret(String secretAddress) {
        return getSecret(secretAddress, false);
    }

    /*
     *
     * Stores encrypted secrets
     * */
    public void addSecret(String secretAddress, String secretValue) {
        secretAddress = secretAddress.toLowerCase();
        Web3Secret web3Secret = new Web3Secret(secretAddress, secretValue);
        encryptSecret(web3Secret);
        log.info("Adding new web3 secret [secretAddress:{}, encryptedSecretValue:{}]",
                secretAddress, web3Secret.getValue());
        web3SecretRepository.save(web3Secret);
    }

}

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

package com.iexec.sms.secret.web2;

import com.iexec.sms.encryption.EncryptionService;
import com.iexec.sms.secret.Secret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class Web2SecretsServiceTests {

    String ownerAddress = "ownerAddress";
    String secretAddress = "secretAddress";
    String plainSecretValue = "plainSecretValue";
    String encryptedSecretValue = "encryptedSecretValue";

    @Mock
    private Web2SecretsRepository web2SecretsRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private Web2SecretsService web2SecretsService;

    @BeforeEach
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void shouldGetAndDecryptWeb2Secrets() {
        ownerAddress = ownerAddress.toLowerCase();
        Secret encryptedSecret = new Secret(secretAddress, encryptedSecretValue);
        encryptedSecret.setEncryptedValue(true);
        List<Secret> secretList = List.of(encryptedSecret);
        Web2Secrets web2SecretsMock = new Web2Secrets(ownerAddress);
        web2SecretsMock.setSecrets(secretList);
        when(web2SecretsRepository.findWeb2SecretsByOwnerAddress(ownerAddress))
                .thenReturn(Optional.of(web2SecretsMock));
        when(encryptionService.decrypt(encryptedSecret.getValue()))
                .thenReturn(plainSecretValue);

        Optional<Secret> result = web2SecretsService.getSecret(ownerAddress, secretAddress, true);
        assertThat(result.get().getAddress()).isEqualTo(secretAddress);
        assertThat(result.get().getValue()).isEqualTo(plainSecretValue);
    }

    @Test
    public void shouldAddSecret() {
        ownerAddress = ownerAddress.toLowerCase();
        web2SecretsService.addSecret(ownerAddress, secretAddress, plainSecretValue);
        verify(web2SecretsRepository, times(1)).save(any());
    }

    @Test
    public void shouldUpdateSecret() {
        ownerAddress = ownerAddress.toLowerCase();
        Secret encryptedSecret = new Secret(secretAddress, encryptedSecretValue);
        encryptedSecret.setEncryptedValue(true);
        String newSecretValue = "newSecretValue";
        String newEncryptedSecretValue = "newEncryptedSecretValue";
        List<Secret> secretList = List.of(encryptedSecret);
        Web2Secrets web2SecretsMock = new Web2Secrets(ownerAddress);
        web2SecretsMock.setSecrets(secretList);
        when(web2SecretsRepository.findWeb2SecretsByOwnerAddress(ownerAddress))
                .thenReturn(Optional.of(web2SecretsMock));
        when(encryptionService.encrypt(newSecretValue))
                .thenReturn(newEncryptedSecretValue);

        web2SecretsService.updateSecret(ownerAddress, secretAddress, newSecretValue);
        verify(web2SecretsRepository, times(1)).save(web2SecretsMock);
    }
}
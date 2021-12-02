/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.sms.secret.compute;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Data
@NoArgsConstructor
@Entity
public class TeeTaskComputeSecret {
    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    private String id;

    /**
     * Represents the blockchain address of the deployed object
     * (0xapplication, 0xdataset, 0xworkerpool)
     * <br>
     * In a future release, it should also handle ENS names.
     */
    private String onChainObjectAddress;
    private OnChainObjectType onChainObjectType;
    private SecretOwnerRole secretOwnerRole;
    private String fixedSecretOwner;  // May be empty if the owner is not fixed
    private long index;
    @Column(columnDefinition = "LONGTEXT")
    private String value;

    @Builder
    public TeeTaskComputeSecret(
            OnChainObjectType onChainObjectType,
            String onChainObjectAddress,
            SecretOwnerRole secretOwnerRole,
            String fixedSecretOwner,
            long index,
            String value) {
        this.onChainObjectType = onChainObjectType;
        this.onChainObjectAddress = onChainObjectAddress;
        this.secretOwnerRole = secretOwnerRole;
        this.fixedSecretOwner = fixedSecretOwner;
        this.index = index;
        this.value = value;
    }
}

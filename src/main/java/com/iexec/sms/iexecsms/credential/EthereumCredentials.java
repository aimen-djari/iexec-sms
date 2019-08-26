package com.iexec.sms.iexecsms.credential;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EthereumCredentials {

    private String address;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BigInteger privateKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BigInteger publicKey;

    public EthereumCredentials(ECKeyPair ecKeyPair) {
        this.address = Numeric.prependHexPrefix(Keys.getAddress(ecKeyPair));
        this.privateKey = ecKeyPair.getPrivateKey();
        this.publicKey = ecKeyPair.getPublicKey();
    }

}



package com.iexec.sms.tee.config;

import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.sms.api.config.GramineServicesProperties;
import com.iexec.sms.api.config.TeeAppProperties;
import com.iexec.sms.api.config.TeeServicesProperties;
import com.iexec.sms.tee.ConditionalOnTeeProvider;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnTeeProvider(providers = TeeEnclaveProvider.GRAMINE)
@Getter
public class GramineInternalServicesConfiguration
        extends GramineServicesProperties {
    public GramineInternalServicesConfiguration(
            TeeAppProperties preComputeProperties,
            TeeAppProperties postComputeProperties) {
        super(preComputeProperties, postComputeProperties);
    }

    @Override
    public TeeServicesProperties getProperties() {
        return new GramineServicesProperties(getPreComputeProperties(), getPostComputeProperties());
    }
}

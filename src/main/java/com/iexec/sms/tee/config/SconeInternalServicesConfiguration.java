package com.iexec.sms.tee.config;

import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.sms.api.config.SconeServicesConfiguration;
import com.iexec.sms.api.config.TeeAppConfiguration;
import com.iexec.sms.api.config.TeeServicesConfiguration;
import com.iexec.sms.tee.ConditionalOnTeeProvider;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.validation.constraints.NotBlank;

@Configuration
@ConditionalOnTeeProvider(providers = TeeEnclaveProvider.SCONE)
@Getter
public class SconeInternalServicesConfiguration
        extends SconeServicesConfiguration
        implements TeeInternalServicesConfiguration{
    public SconeInternalServicesConfiguration(
            TeeAppConfiguration preComputeConfiguration,
            TeeAppConfiguration postComputeConfiguration,
            @Value("${tee.scone.las-image}")
            @NotBlank(message = "las image must be provided")
            String lasImage) {
        super(preComputeConfiguration, postComputeConfiguration, lasImage);
    }

    @Override
    public TeeServicesConfiguration getShareableConfiguration() {
        return new SconeServicesConfiguration(getPreComputeConfiguration(), getPostComputeConfiguration(), getLasImage());
    }
}

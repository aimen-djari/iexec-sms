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

package com.iexec.sms.tee.session.scone;

import com.iexec.sms.tee.session.base.SecretEnclaveBase;
import com.iexec.sms.tee.session.base.SecretSessionBase;
import com.iexec.sms.tee.session.base.SecretSessionBaseService;
import com.iexec.sms.tee.session.generic.TeeSessionGenerationException;
import com.iexec.sms.tee.session.generic.TeeSessionRequest;
import com.iexec.sms.tee.session.scone.cas.CasEnclave;
import com.iexec.sms.tee.session.scone.cas.CasSession;
import com.iexec.sms.tee.session.scone.cas.CasSession.AccessPolicy;
import com.iexec.sms.tee.session.scone.cas.CasSession.Image.Volume;
import com.iexec.sms.tee.session.scone.cas.CasSession.Security;
import com.iexec.sms.tee.session.scone.cas.CasSession.Volumes;
import com.iexec.sms.tee.workflow.TeeWorkflowConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO Rename and move
@Slf4j
@Service
public class SconeSessionMakerService {

    // Internal values required for setting up a palaemon session
    // Generic
    static final String TOLERATED_INSECURE_OPTIONS = "TOLERATED_INSECURE_OPTIONS";
    static final String IGNORED_SGX_ADVISORIES = "IGNORED_SGX_ADVISORIES";
    static final String APP_ARGS = "APP_ARGS";

    // PreCompute
    static final String PRE_COMPUTE_ENTRYPOINT = "PRE_COMPUTE_ENTRYPOINT";
    // PostCompute
    static final String POST_COMPUTE_ENTRYPOINT = "POST_COMPUTE_ENTRYPOINT";

    private final SecretSessionBaseService secretSessionBaseService;
    private final TeeWorkflowConfiguration teeWorkflowConfig;
    private final SconeSessionSecurityConfig attestationSecurityConfig;

    public SconeSessionMakerService(
            SecretSessionBaseService secretSessionBaseService,
            TeeWorkflowConfiguration teeWorkflowConfig,
            SconeSessionSecurityConfig attestationSecurityConfig) {
        this.secretSessionBaseService = secretSessionBaseService;
        this.teeWorkflowConfig = teeWorkflowConfig;
        this.attestationSecurityConfig = attestationSecurityConfig;
    }

    /**
     * Collect tokens required for different compute stages (pre, in, post)
     * and build the yaml config of the TEE session.
     * <p>
     * TODO: Read onchain available infos from enclave instead of copying
     * public vars to palaemon.yml. It needs ssl call from enclave to eth
     * node (only ethereum node address required inside palaemon.yml)
     *
     * @param request session request details
     * @return session config in yaml string format
     */
    public CasSession generateSession(TeeSessionRequest request) throws TeeSessionGenerationException {
        SecretSessionBase baseSession = secretSessionBaseService.getSecretsTokens(request);

        CasEnclave casPreEnclave = toCasSessionEnclave(baseSession.getPreCompute());
        casPreEnclave.setCommand(teeWorkflowConfig.getPreComputeEntrypoint());
        CasEnclave casAppEnclave = toCasSessionEnclave(baseSession.getAppCompute());
        casAppEnclave.setCommand(request.getTaskDescription().getAppCommand());
        CasEnclave casPostEnclave = toCasSessionEnclave(baseSession.getPostCompute());
        casPostEnclave.setCommand(teeWorkflowConfig.getPostComputeEntrypoint());

        addJavaEnvVars(casPreEnclave);
        addJavaEnvVars(casPostEnclave);

        List<String> policy = Arrays.asList("CREATOR");

        CasSession casSession = CasSession.builder()
                .name(request.getSessionId())
                .version("0.3")
                .accessPolicy(new AccessPolicy(policy, policy))
                .services(Arrays.asList(casPreEnclave, casAppEnclave, casPostEnclave))
                .security(new Security(attestationSecurityConfig.getToleratedInsecureOptions(),
                        attestationSecurityConfig.getIgnoredSgxAdvisories()))
                .build();

        Volume iexecInVolume = new Volume("iexec_in", "/iexec_in");
        Volume iexecOutVolume = new Volume("iexec_out", "/iexec_out");
        Volume postComputeTmpVolume = new Volume("post-compute-tmp", "/post-compute-tmp");

        casSession.setVolumes(Arrays.asList(
                new Volumes(iexecInVolume.getName()),
                new Volumes(iexecOutVolume.getName()),
                new Volumes(postComputeTmpVolume.getName())));

        casSession.setImages(Arrays.asList(
                new CasSession.Image(casPreEnclave.getImageName(), Arrays.asList(iexecInVolume)),
                new CasSession.Image(casAppEnclave.getImageName(), Arrays.asList(iexecInVolume, iexecOutVolume)),
                new CasSession.Image(casPostEnclave.getImageName(), Arrays.asList(iexecOutVolume, postComputeTmpVolume))

        ));

        return casSession;
    }

    private void addJavaEnvVars(CasEnclave casSessionEnclave) {
        Map<String, String> additionalJavaEnv = Map.of("LD_LIBRARY_PATH",
                "/usr/lib/jvm/java-11-openjdk/lib/server:/usr/lib/jvm/java-11-openjdk/lib:/usr/lib/jvm/java-11-openjdk/../lib",
                "JAVA_TOOL_OPTIONS", "-Xmx256m");
        HashMap<String, Object> newEnvironment = new HashMap<>();
        newEnvironment.putAll(casSessionEnclave.getEnvironment());
        newEnvironment.putAll(additionalJavaEnv);
        casSessionEnclave.setEnvironment(newEnvironment);
    }

    private CasEnclave toCasSessionEnclave(SecretEnclaveBase enclaveBase) {
        return CasEnclave.builder()
                .name(enclaveBase.getName())
                .imageName(enclaveBase.getName() + "-image")
                .mrenclaves(Arrays.asList(enclaveBase.getMrenclave()))
                .pwd("/")
                // TODO .command(command)
                .environment(enclaveBase.getEnvironment())
                .build();
    }

}

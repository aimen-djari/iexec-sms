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

package com.iexec.sms.tee.session.palaemon;

import com.iexec.common.sms.secret.ReservedSecretKeyName;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveConfiguration;
import com.iexec.common.utils.FileHelper;
import com.iexec.common.utils.IexecEnvUtils;
import com.iexec.sms.secret.Secret;
import com.iexec.sms.secret.compute.OnChainObjectType;
import com.iexec.sms.secret.compute.SecretOwnerRole;
import com.iexec.sms.secret.compute.TeeTaskComputeSecret;
import com.iexec.sms.secret.compute.TeeTaskComputeSecretService;
import com.iexec.sms.secret.web2.Web2SecretsService;
import com.iexec.sms.secret.web3.Web3SecretService;
import com.iexec.sms.tee.challenge.TeeChallenge;
import com.iexec.sms.tee.challenge.TeeChallengeService;
import com.iexec.sms.tee.session.attestation.AttestationSecurityConfig;
import com.iexec.sms.tee.workflow.TeeWorkflowConfiguration;
import com.iexec.sms.utils.EthereumCredentials;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.iexec.common.chain.DealParams.DROPBOX_RESULT_STORAGE_PROVIDER;
import static com.iexec.common.precompute.PreComputeUtils.IEXEC_DATASET_KEY;
import static com.iexec.common.precompute.PreComputeUtils.IS_DATASET_REQUIRED;
import static com.iexec.common.sms.secret.ReservedSecretKeyName.IEXEC_RESULT_ENCRYPTION_PUBLIC_KEY;
import static com.iexec.common.tee.TeeUtils.booleanToYesNo;
import static com.iexec.common.worker.result.ResultUtils.*;
import static java.util.Objects.requireNonNull;

@Slf4j
@Service
public class PalaemonSessionService {

    public static final String EMPTY_YML_VALUE = "";

    // Internal values required for setting up a palaemon session
    // Generic
    static final String SESSION_ID = "SESSION_ID";
    static final String INPUT_FILE_URLS = "INPUT_FILE_URLS";
    static final String INPUT_FILE_NAMES = "INPUT_FILE_NAMES";
    static final String TOLERATED_INSECURE_OPTIONS = "TOLERATED_INSECURE_OPTIONS";
    static final String IGNORED_SGX_ADVISORIES = "IGNORED_SGX_ADVISORIES";
    // PreCompute
    static final String IS_PRE_COMPUTE_REQUIRED = "IS_PRE_COMPUTE_REQUIRED";
    static final String PRE_COMPUTE_MRENCLAVE = "PRE_COMPUTE_MRENCLAVE";
    static final String PRE_COMPUTE_ENTRYPOINT = "PRE_COMPUTE_ENTRYPOINT";
    // Compute
    static final String APP_MRENCLAVE = "APP_MRENCLAVE";
    static final String APP_ARGS = "APP_ARGS";
    static final String IEXEC_APP_DEVELOPER_SECRET_PREFIX = "IEXEC_APP_DEVELOPER_SECRET_";
    static final String IEXEC_REQUESTER_SECRET_PREFIX = "IEXEC_REQUESTER_SECRET_";
    // PostCompute
    static final String POST_COMPUTE_MRENCLAVE = "POST_COMPUTE_MRENCLAVE";
    static final String POST_COMPUTE_ENTRYPOINT = "POST_COMPUTE_ENTRYPOINT";
    // Env
    private static final String ENV_PROPERTY = "env";

    private final Web3SecretService web3SecretService;
    private final Web2SecretsService web2SecretsService;
    private final TeeChallengeService teeChallengeService;
    private final TeeWorkflowConfiguration teeWorkflowConfig;
    private final AttestationSecurityConfig attestationSecurityConfig;
    private final TeeTaskComputeSecretService teeTaskComputeSecretService;

    @Value("${scone.cas.palaemon}")
    private String palaemonTemplateFilePath;

    public PalaemonSessionService(
            Web3SecretService web3SecretService,
            Web2SecretsService web2SecretsService,
            TeeChallengeService teeChallengeService,
            TeeWorkflowConfiguration teeWorkflowConfig,
            AttestationSecurityConfig attestationSecurityConfig,
            TeeTaskComputeSecretService teeTaskComputeSecretService) {
        this.web3SecretService = web3SecretService;
        this.web2SecretsService = web2SecretsService;
        this.teeChallengeService = teeChallengeService;
        this.teeWorkflowConfig = teeWorkflowConfig;
        this.attestationSecurityConfig = attestationSecurityConfig;
        this.teeTaskComputeSecretService = teeTaskComputeSecretService;
    }

    @PostConstruct
    void postConstruct() throws Exception {
        if (StringUtils.isEmpty(palaemonTemplateFilePath)) {
            throw new IllegalArgumentException("Missing palaemon template filepath");
        }
        if (!FileHelper.exists(palaemonTemplateFilePath)) {
            throw new FileNotFoundException("Missing palaemon template file");
        }
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
     * @throws Exception
     */
    public String getSessionYml(PalaemonSessionRequest request) throws Exception {
        requireNonNull(request, "Session request must not be null");
        requireNonNull(request.getTaskDescription(), "Task description must not be null");
        TaskDescription taskDescription = request.getTaskDescription();
        Map<String, Object> palaemonTokens = new HashMap<>();
        palaemonTokens.put(SESSION_ID, request.getSessionId());
        // pre-compute
        boolean isPreComputeRequired = taskDescription.containsDataset() ||
                !taskDescription.getInputFiles().isEmpty();
        palaemonTokens.put(IS_PRE_COMPUTE_REQUIRED, isPreComputeRequired);
        if (isPreComputeRequired) {
            palaemonTokens.putAll(getPreComputePalaemonTokens(request));
        }
        // app
        palaemonTokens.putAll(getAppPalaemonTokens(request));
        // post compute
        palaemonTokens.putAll(getPostComputePalaemonTokens(request));
        // env variables
        Map<String, String> env = IexecEnvUtils.getAllIexecEnv(taskDescription);
        // Null value should be replaced by an empty string.
        env.forEach((key, value) -> env.replace(key, null, EMPTY_YML_VALUE));
        palaemonTokens.put(ENV_PROPERTY, env);
        // Add attestation security config
        String toleratedInsecureOptions =
                String.join(",", attestationSecurityConfig.getToleratedInsecureOptions());
        String ignoredSgxAdvisories =
                String.join(",", attestationSecurityConfig.getIgnoredSgxAdvisories());
        palaemonTokens.put(TOLERATED_INSECURE_OPTIONS, toleratedInsecureOptions);
        palaemonTokens.put(IGNORED_SGX_ADVISORIES, ignoredSgxAdvisories);
        // Merge template with tokens and return the result
        return getFilledPalaemonTemplate(this.palaemonTemplateFilePath, palaemonTokens);
    }

    /**
     * Get tokens to be injected in the pre-compute enclave.
     *
     * @param request
     * @return map of pre-compute tokens
     * @throws Exception if dataset secret is not found.
     */
    Map<String, Object> getPreComputePalaemonTokens(PalaemonSessionRequest request)
            throws Exception {
        TaskDescription taskDescription = request.getTaskDescription();
        String taskId = taskDescription.getChainTaskId();
        Map<String, Object> tokens = new HashMap<>();
        String fingerprint = teeWorkflowConfig.getPreComputeFingerprint();
        tokens.put(PRE_COMPUTE_MRENCLAVE, fingerprint);
        String entrypoint = teeWorkflowConfig.getPreComputeEntrypoint();
        tokens.put(PRE_COMPUTE_ENTRYPOINT, entrypoint);
        tokens.put(IS_DATASET_REQUIRED, taskDescription.containsDataset());
        tokens.put(IEXEC_DATASET_KEY, EMPTY_YML_VALUE);
        if (taskDescription.containsDataset()) {
            String datasetKey = web3SecretService
                    .getSecret(taskDescription.getDatasetAddress(), true)
                    .orElseThrow(() -> new Exception("Empty dataset secret - taskId: " + taskId))
                    .getTrimmedValue();
            tokens.put(IEXEC_DATASET_KEY, datasetKey);
        } else {
            log.info("No dataset key needed for this task [taskId:{}]", taskId);
        }
        // extract <IEXEC_INPUT_FILE_URL_N, url>
        // this map will be empty (not null) if no input file is found
        Map<String, String> inputFileUrls = IexecEnvUtils.getAllIexecEnv(taskDescription)
                .entrySet()
                .stream()
                .filter(e -> e.getKey().contains(IexecEnvUtils.IEXEC_INPUT_FILE_URL_PREFIX))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        tokens.put(INPUT_FILE_URLS, inputFileUrls);
        return tokens;
    }

    /*
     * Compute (App)
     */
    Map<String, Object> getAppPalaemonTokens(PalaemonSessionRequest request) {
        TaskDescription taskDescription = request.getTaskDescription();
        requireNonNull(taskDescription, "Task description must no be null");
        Map<String, Object> tokens = new HashMap<>();
        TeeEnclaveConfiguration enclaveConfig = taskDescription.getAppEnclaveConfiguration();
        requireNonNull(enclaveConfig, "Enclave configuration must no be null");
        if (!enclaveConfig.getValidator().isValid()){
            throw new IllegalArgumentException("Invalid enclave configuration: " +
                    enclaveConfig.getValidator().validate().toString());
        }
        tokens.put(APP_MRENCLAVE, enclaveConfig.getFingerprint());
        String appArgs = enclaveConfig.getEntrypoint();
        if (!StringUtils.isEmpty(taskDescription.getCmd())) {
            appArgs = appArgs + " " + taskDescription.getCmd();
        }
        tokens.put(APP_ARGS, appArgs);
        // extract <IEXEC_INPUT_FILE_NAME_N, name>
        // this map will be empty (not null) if no input file is found
        Map<String, String> inputFileNames = IexecEnvUtils.getComputeStageEnvMap(taskDescription)
                .entrySet()
                .stream()
                .filter(e -> e.getKey().contains(IexecEnvUtils.IEXEC_INPUT_FILE_NAME_PREFIX))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        tokens.put(INPUT_FILE_NAMES, inputFileNames);

        final Map<String, Object> computeSecrets = getApplicationComputeSecrets(taskDescription);
        tokens.putAll(computeSecrets);

        return tokens;
    }

    private Map<String, Object> getApplicationComputeSecrets(TaskDescription taskDescription) {
        Map<String, Object> tokens = new HashMap<>();

        final long secretIndex = 0;
        String appDeveloperSecret0 =
                teeTaskComputeSecretService.getSecret(
                                OnChainObjectType.APPLICATION,
                                taskDescription.getAppAddress(),
                                SecretOwnerRole.APPLICATION_DEVELOPER,
                                "",
                                secretIndex)
                        .map(TeeTaskComputeSecret::getValue)
                        .orElse(EMPTY_YML_VALUE);
        tokens.put(IEXEC_APP_DEVELOPER_SECRET_PREFIX + secretIndex, appDeveloperSecret0);

        String requesterSecret0 =
                teeTaskComputeSecretService.getSecret(
                                OnChainObjectType.APPLICATION,
                                taskDescription.getAppAddress(),
                                SecretOwnerRole.REQUESTER,
                                taskDescription.getRequester(),
                                secretIndex)
                        .map(TeeTaskComputeSecret::getValue)
                        .orElse(EMPTY_YML_VALUE);
        tokens.put(IEXEC_REQUESTER_SECRET_PREFIX + secretIndex, requesterSecret0);

        return tokens;
    }

    /*
     * Post-Compute (Result)
     */
    Map<String, String> getPostComputePalaemonTokens(PalaemonSessionRequest request)
            throws Exception {
        TaskDescription taskDescription = request.getTaskDescription();
        requireNonNull(taskDescription, "Task description must no be null");
        String taskId = taskDescription.getChainTaskId();
        Map<String, String> tokens = new HashMap<>();
        String teePostComputeFingerprint = teeWorkflowConfig.getPostComputeFingerprint();
        // ###############################################################################
        // TODO: activate this when user specific post-compute is properly
        // supported. See https://github.com/iExecBlockchainComputing/iexec-sms/issues/52.
        // ###############################################################################
        // // Use specific post-compute image if requested.
        //if (taskDescription.containsPostCompute()) {
        //    teePostComputeFingerprint = taskDescription.getTeePostComputeFingerprint();
        //    //add entrypoint too
        //}
        tokens.put(POST_COMPUTE_MRENCLAVE, teePostComputeFingerprint);
        String entrypoint = teeWorkflowConfig.getPostComputeEntrypoint();
        tokens.put(POST_COMPUTE_ENTRYPOINT, entrypoint);
        // encryption
        Map<String, String> encryptionTokens = getPostComputeEncryptionTokens(request);
        if (encryptionTokens.isEmpty()) {
            throw new Exception("Failed to get post-compute encryption tokens - taskId: " + taskId);
        }
        tokens.putAll(encryptionTokens);
        // storage
        Map<String, String> storageTokens = getPostComputeStorageTokens(request);
        if (storageTokens.isEmpty()) {
            throw new Exception("Failed to get post-compute storage tokens - taskId: " + taskId);
        }
        tokens.putAll(storageTokens);
        // enclave signature
        Map<String, String> signTokens = getPostComputeSignTokens(request);
        if (signTokens.isEmpty()) {
            throw new Exception("Failed to get post-compute signature tokens - taskId: " + taskId);
        }
        tokens.putAll(signTokens);
        return tokens;
    }

    Map<String, String> getPostComputeEncryptionTokens(PalaemonSessionRequest request)
            throws Exception {
        TaskDescription taskDescription = request.getTaskDescription();
        String taskId = taskDescription.getChainTaskId();
        Map<String, String> tokens = new HashMap<>();
        boolean shouldEncrypt = taskDescription.isResultEncryption();
        // TODO use boolean with quotes instead of yes/no
        tokens.put(RESULT_ENCRYPTION, booleanToYesNo(shouldEncrypt));
        tokens.put(RESULT_ENCRYPTION_PUBLIC_KEY, EMPTY_YML_VALUE);
        if (!shouldEncrypt) {
            return tokens;
        }
        Optional<Secret> beneficiaryResultEncryptionKeySecret = web2SecretsService.getSecret(
                taskDescription.getBeneficiary(),
                IEXEC_RESULT_ENCRYPTION_PUBLIC_KEY,
                true);
        if (beneficiaryResultEncryptionKeySecret.isEmpty()) {
            throw new Exception("Empty beneficiary encryption key - taskId: " + taskId);
        }
        String publicKeyValue = beneficiaryResultEncryptionKeySecret.get().getTrimmedValue();
        tokens.put(RESULT_ENCRYPTION_PUBLIC_KEY, publicKeyValue); // base64 encoded by client
        return tokens;
    }

    // TODO: We need a signature of the beneficiary to push
    // to the beneficiary private storage space waiting for
    // that feature we only allow to push to the requester
    // private storage space
    Map<String, String> getPostComputeStorageTokens(PalaemonSessionRequest request)
            throws Exception {
        TaskDescription taskDescription = request.getTaskDescription();
        String taskId = taskDescription.getChainTaskId();
        Map<String, String> tokens = new HashMap<>();
        boolean isCallbackRequested = taskDescription.containsCallback();
        tokens.put(RESULT_STORAGE_CALLBACK, booleanToYesNo(isCallbackRequested));
        tokens.put(RESULT_STORAGE_PROVIDER, EMPTY_YML_VALUE);
        tokens.put(RESULT_STORAGE_PROXY, EMPTY_YML_VALUE);
        tokens.put(RESULT_STORAGE_TOKEN, EMPTY_YML_VALUE);
        if (isCallbackRequested) {
            return tokens;
        }
        String storageProvider = taskDescription.getResultStorageProvider();
        String storageProxy = taskDescription.getResultStorageProxy();
        String keyName = storageProvider.equals(DROPBOX_RESULT_STORAGE_PROVIDER)
                ? ReservedSecretKeyName.IEXEC_RESULT_DROPBOX_TOKEN
                : ReservedSecretKeyName.IEXEC_RESULT_IEXEC_IPFS_TOKEN;
        Optional<Secret> requesterStorageTokenSecret =
                web2SecretsService.getSecret(taskDescription.getRequester(), keyName, true);
        if (requesterStorageTokenSecret.isEmpty()) {
            log.error("Failed to get storage token [taskId:{}, storageProvider:{}, requester:{}]",
                    taskId, storageProvider, taskDescription.getRequester());
            throw new Exception("Empty requester storage token - taskId: " + taskId);
        }
        String requesterStorageToken = requesterStorageTokenSecret.get().getTrimmedValue();
        tokens.put(RESULT_STORAGE_PROVIDER, storageProvider);
        tokens.put(RESULT_STORAGE_PROXY, storageProxy);
        tokens.put(RESULT_STORAGE_TOKEN, requesterStorageToken);
        return tokens;
    }

    Map<String, String> getPostComputeSignTokens(PalaemonSessionRequest request)
            throws Exception {
        String taskId = request.getTaskDescription().getChainTaskId();
        String workerAddress = request.getWorkerAddress();
        Map<String, String> tokens = new HashMap<>();
        if (StringUtils.isEmpty(workerAddress)) {
            throw new Exception("Empty worker address - taskId: " + taskId);
        }
        if (StringUtils.isEmpty(request.getEnclaveChallenge())) {
            throw new Exception("Empty public enclave challenge - taskId: " + taskId);
        }
        Optional<TeeChallenge> teeChallenge = teeChallengeService.getOrCreate(taskId, true);
        if (teeChallenge.isEmpty()) {
            throw new Exception("Empty TEE challenge  - taskId: " + taskId);
        }
        EthereumCredentials enclaveCredentials = teeChallenge.get().getCredentials();
        if (enclaveCredentials == null || enclaveCredentials.getPrivateKey().isEmpty()) {
            throw new Exception("Empty TEE challenge credentials - taskId: " + taskId);
        }
        tokens.put(RESULT_TASK_ID, taskId);
        tokens.put(RESULT_SIGN_WORKER_ADDRESS, workerAddress);
        tokens.put(RESULT_SIGN_TEE_CHALLENGE_PRIVATE_KEY, enclaveCredentials.getPrivateKey());
        return tokens;
    }

    private String getFilledPalaemonTemplate(String templatePath, Map<String, Object> tokens) {
        VelocityEngine ve = new VelocityEngine();
        ve.init();
        Template template = ve.getTemplate(templatePath);
        VelocityContext context = new VelocityContext();
        tokens.forEach(context::put); // copy all data from the tokens into context
        StringWriter writer = new StringWriter();
        template.merge(context, writer);
        return writer.toString();
    }
}

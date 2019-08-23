package com.iexec.sms.iexecsms.execution;


import com.iexec.common.security.Signature;
import com.iexec.common.sms.SmsRequest;
import com.iexec.common.sms.SmsRequestData;
import com.iexec.common.sms.secrets.SmsSecretResponse;
import com.iexec.common.sms.secrets.SmsSecretResponseData;
import com.iexec.sms.iexecsms.authorization.Authorization;
import com.iexec.sms.iexecsms.authorization.AuthorizationService;
import com.iexec.sms.iexecsms.cas.CasPalaemonHelperService;
import com.iexec.sms.iexecsms.cas.CasService;
import com.iexec.sms.iexecsms.secret.SecretFolderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
public class ExecutionController {

    private static final String DOMAIN = "IEXEC_SMS_DOMAIN";//TODO: Add session salt after domain
    private final CasPalaemonHelperService casPalaemonHelperService;
    private final CasService casService;
    private SecretFolderService secretFolderService;
    private AuthorizationService authorizationService;

    public ExecutionController(
            SecretFolderService secretFolderService,
            AuthorizationService authorizationService,
            CasPalaemonHelperService casPalaemonHelperService,
            CasService casService) {
        this.secretFolderService = secretFolderService;
        this.authorizationService = authorizationService;
        this.casPalaemonHelperService = casPalaemonHelperService;
        this.casService = casService;
    }

    /*
     * Retrieve secrets when non-tee execution
     * */
    @GetMapping("/executions/nontee/secrets")
    public ResponseEntity getNonTeeExecutionSecretsV2(@RequestBody SmsRequest smsRequest) {
        // Check that the demand is legitimate -> move workerSignature outside of authorization
        // see secret controller for auth
        SmsRequestData data = smsRequest.getSmsSecretRequestData();
        Authorization authorization = Authorization.builder()
                .chainTaskId(data.getChainTaskId())
                .enclaveAddress(data.getEnclaveChallenge())
                .workerAddress(data.getWorkerAddress())
                .workerSignature(new Signature(data.getWorkerSignature()))//move this
                .workerpoolSignature(new Signature(data.getCoreSignature())).build();

        if (!authorizationService.isAuthorizedToGetKeys(authorization)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        SmsSecretResponseData nonTeeSecrets = SmsSecretResponseData.builder().build();
        //TODO: fetch secrets - secretFolderService.getSecret("xx") for each alias
        SmsSecretResponse smsSecretResponse = SmsSecretResponse.builder()
                .data(nonTeeSecrets)
                .build();

        return Optional.of(smsSecretResponse).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /*
     * Retrieve session when tee execution
     * */
    @GetMapping("/executions/tee/session")
    public ResponseEntity getTeeExecutionSessionV2(@RequestBody SmsRequest smsRequest) throws Exception {
        // Check that the demand is legitimate -> move workerSignature outside of authorization
        // see secret controller for auth
        SmsRequestData data = smsRequest.getSmsSecretRequestData();
        Authorization authorization = Authorization.builder()
                .chainTaskId(data.getChainTaskId())
                .enclaveAddress(data.getEnclaveChallenge())
                .workerAddress(data.getWorkerAddress())
                .workerSignature(new Signature(data.getWorkerSignature()))//move this
                .workerpoolSignature(new Signature(data.getCoreSignature())).build();

        if (!authorizationService.isAuthorizedToGetKeys(authorization)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        String taskId = smsRequest.getSmsSecretRequestData().getChainTaskId();
        String workerAddress = smsRequest.getSmsSecretRequestData().getWorkerAddress();
        String attestingEnclave = smsRequest.getSmsSecretRequestData().getEnclaveChallenge();
        String configFile = casPalaemonHelperService.getPalaemonConfigurationFile(taskId, workerAddress, attestingEnclave);
        System.out.println(configFile);

        // TODO: check if we should just not simply return the sessionID
        // TODO: return appropriate type
        ResponseEntity responseEntity = casService.generateSecureSessionWithPalaemonFile(configFile);

        return responseEntity;
    }


    /*
     * Remove this by replacing generateSecureSessionV1 by getTeeExecutionSessionV2
     * */
    @PostMapping("/sessions/generate")
    public ResponseEntity generateSecureSessionV1(@RequestBody SmsRequest smsRequest) throws Exception {
        String taskId = smsRequest.getSmsSecretRequestData().getChainTaskId();
        String workerAddress = smsRequest.getSmsSecretRequestData().getWorkerAddress();
        String attestingEnclave = smsRequest.getSmsSecretRequestData().getEnclaveChallenge();
        String configFile = casPalaemonHelperService.getPalaemonConfigurationFile(taskId, workerAddress, attestingEnclave);
        System.out.println(configFile);

        // TODO: check if we should just not simply return the sessionID
        return casService.generateSecureSessionWithPalaemonFile(configFile);
    }

}


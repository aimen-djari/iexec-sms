/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

package com.iexec.sms.tee.session.gramine;

import com.iexec.common.task.TaskDescription;
import com.iexec.sms.api.TeeSessionGenerationError;
import com.iexec.sms.tee.session.TeeSecretsSessionRequest;
import com.iexec.sms.tee.session.TeeSessionGenerationException;
import com.iexec.sms.tee.session.TeeSessionLogConfiguration;
import com.iexec.sms.tee.session.gramine.sps.SpsApiClient;
import com.iexec.sms.tee.session.gramine.sps.SpsConfiguration;
import com.iexec.sms.tee.session.gramine.sps.SpsSession;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
public class GramineSessionHandlerServiceTests {

    @Mock
    private GramineSessionMakerService sessionService;
    @Mock
    private SpsConfiguration spsConfiguration;
    @Mock
    private TeeSessionLogConfiguration teeSessionLogConfiguration;
    @InjectMocks
    private GramineSessionHandlerService gramineSessionHandlerService;

    @BeforeEach
    void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldBuildAndPostSession(CapturedOutput output) throws TeeSessionGenerationException {
        TeeSecretsSessionRequest request = mock(TeeSecretsSessionRequest.class);
        TaskDescription taskDescription = mock(TaskDescription.class);
        when(request.getTaskDescription()).thenReturn(taskDescription);
        SpsSession spsSession = mock(SpsSession.class);
        when(spsSession.toString()).thenReturn("sessionContent");
        when(sessionService.generateSession(request)).thenReturn(spsSession);
        when(teeSessionLogConfiguration.isDisplayDebugSessionEnabled()).thenReturn(true);
        SpsApiClient spsClient = mock(SpsApiClient.class);
        when(spsClient.postSession(spsSession)).thenReturn("sessionId");
        when(spsConfiguration.getInstanceWithBasicAuth()).thenReturn(spsClient);

        assertDoesNotThrow(() -> gramineSessionHandlerService.buildAndPostSession(request));
        // Testing output here since it reflects a business feature (ability to catch a
        // session in debug mode)
        assertTrue(output.getOut().contains("Session content [taskId:null]\nsessionContent\n"));
    }

    @Test
    void shouldNotBuildAndPostSessionSinceBuildSessionFailed()
            throws TeeSessionGenerationException {
        TeeSecretsSessionRequest request = mock(TeeSecretsSessionRequest.class);
        SpsSession spsSession = mock(SpsSession.class);
        when(spsSession.toString()).thenReturn("sessionContent");
        TeeSessionGenerationException teeSessionGenerationException = new TeeSessionGenerationException(
                TeeSessionGenerationError.SECURE_SESSION_GENERATION_FAILED, "some error");
        when(sessionService.generateSession(request)).thenThrow(teeSessionGenerationException);

        assertThrows(teeSessionGenerationException.getClass(),
                () -> gramineSessionHandlerService.buildAndPostSession(request));
    }

    @Test
    void shouldNotBuildAndPostSessionSincePostSessionFailed()
            throws TeeSessionGenerationException {
        TeeSecretsSessionRequest request = mock(TeeSecretsSessionRequest.class);
        TaskDescription taskDescription = mock(TaskDescription.class);
        when(request.getTaskDescription()).thenReturn(taskDescription);
        SpsSession spsSession = mock(SpsSession.class);
        when(spsSession.toString()).thenReturn("sessionContent");
        when(sessionService.generateSession(request)).thenReturn(spsSession);
        when(teeSessionLogConfiguration.isDisplayDebugSessionEnabled()).thenReturn(true);
        SpsApiClient spsClient = mock(SpsApiClient.class);
        when(spsConfiguration.getInstanceWithBasicAuth()).thenReturn(spsClient);
        FeignException apiClientException = mock(FeignException.class);
        when(spsClient.postSession(spsSession)).thenThrow(apiClientException);

        assertThrows(TeeSessionGenerationException.class,
                () -> gramineSessionHandlerService.buildAndPostSession(request));
    }

}

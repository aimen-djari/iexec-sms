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

package com.iexec.sms.utils.version;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.info.BuildProperties;

public class VersionServiceTest {

    @Mock
    private BuildProperties buildProperties;

    @InjectMocks
    private VersionService versionService;

    @BeforeEach
    public void preflight() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @ValueSource(strings={"x.y.z", "x.y.z-rc"})
    void testNonSnapshotVersion(String version) {
        Mockito.when(buildProperties.getVersion()).thenReturn(version);
        Assertions.assertEquals(version, versionService.getVersion());
        Assertions.assertFalse(versionService.isSnapshot());
    }

    @Test
    void testSnapshotVersion() {
        Mockito.when(buildProperties.getVersion()).thenReturn("x.y.z-NEXT-SNAPSHOT");
        Assertions.assertEquals("x.y.z-NEXT-SNAPSHOT", versionService.getVersion());
        Assertions.assertTrue(versionService.isSnapshot());
    }

}

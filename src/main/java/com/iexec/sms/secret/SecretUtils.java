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

package com.iexec.sms.secret;

import java.nio.charset.StandardCharsets;

public abstract class SecretUtils {
    /**
     * Max size (in kBs) a secret can have.
     */
    private static final long SECRET_MAX_SIZE = 4096;

    public static boolean isSecretSizeValid(String secretValue) {
        return secretValue.getBytes(StandardCharsets.UTF_8).length <= SECRET_MAX_SIZE;
    }
}

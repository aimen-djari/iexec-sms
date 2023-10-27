/*
 * Copyright 2023 IEXEC BLOCKCHAIN TECH
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
package com.iexec.sms.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.h2.tools.Script;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Service
public class AdminService {

    // Used to print formatted date in log
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final String datasourceUrl;
    private final String datasourceUsername;
    private final String datasourcePassword;

    public AdminService(@Value("${spring.datasource.url}") String datasourceUrl,
                        @Value("${spring.datasource.username}") String datasourceUsername,
                        @Value("${spring.datasource.password}") String datasourcePassword) {
        this.datasourceUrl = datasourceUrl;
        this.datasourceUsername = datasourceUsername;
        this.datasourcePassword = datasourcePassword;
    }

    /**
     * Creates a backup of the H2 database and saves it to the specified location.
     *
     * @param storageLocation The location where the backup file will be saved, must be an existing directory.
     * @param backupFileName  The name of the backup file.
     * @return {@code true} if the backup was successful; {@code false} if any error occurs.
     */
    boolean createDatabaseBackupFile(String storageLocation, String backupFileName) {
        // Check for missing or empty storageLocation parameter
        if (StringUtils.isBlank(storageLocation)) {
            log.error("storageLocation must not be empty.");
            return false;
        }
        // Check for missing or empty backupFileName parameter
        if (StringUtils.isBlank(backupFileName)) {
            log.error("backupFileName must not be empty.");
            return false;
        }
        // Ensure that storageLocation ends with a slash
        if (!storageLocation.endsWith("/")) {
            storageLocation += "/";
        }

        // Check if storageLocation is an existing directory, we don't want to create it.
        final File directory = new File(storageLocation);
        if (!directory.isDirectory()) {
            log.error("storageLocation must be an existing directory [storageLocation:{}]", storageLocation);
            return false;
        }

        return databaseDump(storageLocation + backupFileName);
    }

    /**
     * @param fullBackupFileName complete fileName (location and filename)
     * @return {@code true} if the backup was successful; {@code false} if any error occurs.
     */
    boolean databaseDump(String fullBackupFileName) {

        if (fullBackupFileName == null || fullBackupFileName.isEmpty()) {
            log.error("fullBackupFileName must not be empty.");
            return false;
        }

        try {
            log.info("Starting the backup process [fullBackupFileName:{}]", fullBackupFileName);
            final long start = System.currentTimeMillis();
            Script.process(datasourceUrl, datasourceUsername, datasourcePassword, fullBackupFileName, "DROP", "");
            final long stop = System.currentTimeMillis();
            final long size = (new File(fullBackupFileName)).length();
            log.info("New backup created [timestamp:{}, duration:{} ms, size:{}, fullBackupFileName:{}]", dateFormat.format(new Date(start)), stop - start, size, fullBackupFileName);
        } catch (SQLException e) {
            log.error("SQL error occurred during backup", e);
            return false;
        }
        return true;
    }

    public String replicateDatabaseBackupFile(String storageID, String fileName) {
        return "replicateDatabaseBackupFile is not implemented";
    }

    public String restoreDatabaseFromBackupFile(String storageId, String fileName) {
        return "restoreDatabaseFromBackupFile is not implemented";
    }
}

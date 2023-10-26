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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.FileSystemNotFoundException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@RestController("/admin")
public class AdminController {

    private final ReentrantLock rLock = new ReentrantLock(true);

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Endpoint to initiate a database backup.
     * <p>
     * This method allows the client to trigger a database backup operation.
     * The backup process will create a snapshot of the current database
     * and store it for future recovery purposes.
     *
     * @return A response entity indicating the status and details of the backup operation.
     * <ul>
     * <li>HTTP 201 (Created) - If the backup has been successfully created.
     * <li>HTTP 429 (Too Many Requests) - If another operation (backup/restore/delete) is already in progress.
     * <li>HTTP 500 (Internal Server Error) - If an unexpected error occurs during the backup process.
     * </ul>
     */
    @PostMapping("/backup")
    public ResponseEntity<String> createBackup() {
        try {
            if (tryToAcquireLock()) {
                return ResponseEntity.ok(adminService.createDatabaseBackupFile());
            } else {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            tryToReleaseLock();
        }
    }

    /**
     * Endpoint to replicate a database backup.
     * <p>
     * This method allows the replication of the backup toward another storage.
     *
     * @param storageID The unique identifier for the storage location of the dump in hexadecimal.
     * @param fileName  The name of the file copied on the persistent storage.
     * @return A response entity indicating the status and details of the replication operation.
     * <ul>
     * <li>HTTP 200 (OK) - If the backup has been successfully replicated.
     * <li>HTTP 400 (Bad Request) - If {@code fileName} is missing or {@code storageID} does not match an existing directory.
     * <li>HTTP 404 (Not Found) - If the backup file specified by {@code fileName} does not exist.
     * <li>HTTP 429 (Too Many Requests) - If another operation (backup/restore/delete) is already in progress.
     * <li>HTTP 500 (Internal Server Error) - If an unexpected error occurs during the replication process.
     * </ul>
     */
    @PostMapping("/{storageID}/replicate-backup")
    public ResponseEntity<String> replicateBackup(@PathVariable String storageID, @RequestParam String fileName) {
        try {
            if (StringUtils.isBlank(storageID) || StringUtils.isBlank(fileName)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!tryToAcquireLock()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            return ResponseEntity.ok(adminService.replicateDatabaseBackupFile(storageID, fileName));
        } catch (FileSystemNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            tryToReleaseLock();
        }
    }

    /**
     * Endpoint to restore a database backup.
     * <p>
     * This method allows the client to initiate the restoration of a database backup
     * from a specified dump file, identified by the {@code fileName}, located in a location specified by the {@code storageID}.
     *
     * @param storageID The unique identifier for the storage location of the dump in hexadecimal.
     * @param fileName  The name of the dump file to be restored.
     * @return A response entity indicating the status and details of the restore operation.
     * <ul>
     * <li>HTTP 200 (OK) - If the backup has been successfully restored.
     * <li>HTTP 400 (Bad Request) - If {@code fileName} is missing or {@code storageID} does not match an existing directory.
     * <li>HTTP 404 (Not Found) - If the backup file specified by {@code fileName} does not exist.
     * <li>HTTP 429 (Too Many Requests) - If another operation (backup/restore/delete) is already in progress.
     * <li>HTTP 500 (Internal Server Error) - If an unexpected error occurs during the restore process.
     * </ul>
     */
    @PostMapping("/{storageID}/restore-backup")
    public ResponseEntity<String> restoreBackup(@PathVariable String storageID, @RequestParam String fileName) {
        try {
            if (StringUtils.isBlank(storageID) || StringUtils.isBlank(fileName)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            if (!tryToAcquireLock()) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
            return ResponseEntity.ok(adminService.restoreDatabaseFromBackupFile(storageID, fileName));
        } catch (FileSystemNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            tryToReleaseLock();
        }
    }

    private boolean tryToAcquireLock() throws InterruptedException {
        return rLock.tryLock(100, TimeUnit.MILLISECONDS);
    }

    private void tryToReleaseLock() {
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
        }
    }

}

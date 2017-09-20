/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.common.health;

import java.io.IOException;

/**
 * Created by kandha on 9/7/17.
 */
public class HealthReporterException extends RuntimeException {
    public HealthReporterException(IOException e) {
    }
}
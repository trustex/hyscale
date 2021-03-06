/**
 * Copyright 2019 Pramati Prism, Inc.
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
package io.hyscale.controller.validator.impl;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.hyscale.commons.exception.HyscaleException;
import io.hyscale.commons.logger.WorkflowLogger;
import io.hyscale.commons.models.Activity;
import io.hyscale.commons.models.HyscaleSpecType;
import io.hyscale.controller.activity.ValidatorActivity;
import io.hyscale.controller.util.ServiceSpecUtil;
import io.hyscale.controller.validator.SpecSchemaValidator;
import io.hyscale.servicespec.commons.activity.ServiceSpecActivity;

/**
 * Provides Service spec schema related implementation to {@link SpecSchemaValidator}
 *
 */
@Component
public class HSpecSchemaValidator extends SpecSchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(HSpecSchemaValidator.class);

    @Override
    public HyscaleSpecType getReferenceSchemaType() {
        return HyscaleSpecType.SERVICE;
    }

    @Override
    public boolean validateData(File serviceSpecFile) throws HyscaleException {
        String serviceFileName = serviceSpecFile.getName();
        String serviceNameFromFile = serviceFileName.split("\\.")[0];
        String serviceName = ServiceSpecUtil.getServiceName(serviceSpecFile);
        if (!serviceNameFromFile.equals(serviceName)) {
            logger.warn(ServiceSpecActivity.SERVICE_NAME_MISMATCH.getActivityMessage(), serviceFileName);
            WorkflowLogger.persist(ServiceSpecActivity.SERVICE_NAME_MISMATCH, serviceFileName);
        }
        return true;
    }

    @Override
    protected Activity getActivity() {
        return ValidatorActivity.SERVICE_SPEC_VALIDATION_FAILED;
    }

}

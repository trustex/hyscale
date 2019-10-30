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
package io.hyscale.servicespec.commons.model.service;

import java.util.Map;
import java.util.Set;

public class Secrets {

	private Set<String> secretKeys;
	private Map<String, String> secretsMap;

	public Set<String> getSecretKeys() {
		return secretKeys;
	}

	public void setSecretKeys(Set<String> secretKeys) {
		this.secretKeys = secretKeys;
	}

	public Map<String, String> getSecretsMap() {
		return secretsMap;
	}

	public void setSecretsMap(Map<String, String> secretsMap) {
		this.secretsMap = secretsMap;
	}
}
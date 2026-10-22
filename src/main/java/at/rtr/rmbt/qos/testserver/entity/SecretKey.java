/*******************************************************************************
 * Copyright RTR-GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package at.rtr.rmbt.qos.testserver.entity;

/**
 * A key client tokens may be signed with.
 * @param label name used in log messages (e.g. the config property name), never the key itself
 * @param key the shared secret
 */
public record SecretKey(String label, String key) {

	@Override
	public String toString() {
		return "SecretKey [label=" + label + "]";
	}
}

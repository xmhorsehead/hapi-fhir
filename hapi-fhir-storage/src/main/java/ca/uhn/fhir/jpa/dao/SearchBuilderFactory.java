/*-
 * #%L
 * HAPI FHIR Storage api
 * %%
 * Copyright (C) 2014 - 2025 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.dao;

import ca.uhn.fhir.rest.api.server.storage.IResourcePersistentId;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class SearchBuilderFactory<T extends IResourcePersistentId<?>> {

	@Autowired
	private ApplicationContext myApplicationContext;

	public SearchBuilderFactory() {}

	public ISearchBuilder<T> newSearchBuilder(String theResourceName, Class<? extends IBaseResource> theResourceType) {
		return (ISearchBuilder<T>)
				myApplicationContext.getBean(ISearchBuilder.SEARCH_BUILDER_BEAN_NAME, theResourceName, theResourceType);
	}
}

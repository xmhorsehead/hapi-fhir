/*
 * #%L
 * HAPI FHIR - Core Library
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
package ca.uhn.fhir.context;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuntimeChildChoiceDefinition extends BaseRuntimeDeclaredChildDefinition {

	private List<Class<? extends IBase>> myChoiceTypes;
	private Map<String, BaseRuntimeElementDefinition<?>> myNameToChildDefinition;
	private Map<Class<? extends IBase>, String> myDatatypeToElementName;
	private Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> myDatatypeToElementDefinition;
	private String myReferenceSuffix;
	private List<Class<? extends IBaseResource>> myResourceTypes;
	private List<Class<? extends IBase>> mySpecializationChoiceTypes = Collections.emptyList();

	/**
	 * Constructor
	 */
	public RuntimeChildChoiceDefinition(
			Field theField,
			String theElementName,
			Child theChildAnnotation,
			Description theDescriptionAnnotation,
			List<Class<? extends IBase>> theChoiceTypes) {
		super(theField, theChildAnnotation, theDescriptionAnnotation, theElementName);

		myChoiceTypes = Collections.unmodifiableList(theChoiceTypes);
	}

	/**
	 * Constructor
	 *
	 * For extension, if myChoiceTypes will be set some other way
	 */
	RuntimeChildChoiceDefinition(
			Field theField, String theElementName, Child theChildAnnotation, Description theDescriptionAnnotation) {
		super(theField, theChildAnnotation, theDescriptionAnnotation, theElementName);
	}

	void setChoiceTypes(
			@Nonnull List<Class<? extends IBase>> theChoiceTypes,
			@Nonnull List<Class<? extends IBase>> theSpecializationChoiceTypes) {
		Validate.notNull(theChoiceTypes, "theChoiceTypes must not be null");
		Validate.notNull(theSpecializationChoiceTypes, "theSpecializationChoiceTypes must not be null");
		myChoiceTypes = Collections.unmodifiableList(theChoiceTypes);
		mySpecializationChoiceTypes = Collections.unmodifiableList(theSpecializationChoiceTypes);
	}

	public List<Class<? extends IBase>> getChoices() {
		return myChoiceTypes;
	}

	@Override
	public Set<String> getValidChildNames() {
		return myNameToChildDefinition.keySet();
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildByName(String theName) {
		assert myNameToChildDefinition.containsKey(theName)
				: "Can't find child '" + theName + "' in names: " + myNameToChildDefinition.keySet();

		return myNameToChildDefinition.get(theName);
	}

	@SuppressWarnings("unchecked")
	@Override
	void sealAndInitialize(
			FhirContext theContext,
			Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions) {
		myNameToChildDefinition = new HashMap<>();
		myDatatypeToElementName = new HashMap<>();
		myDatatypeToElementDefinition = new HashMap<>();
		myResourceTypes = new ArrayList<>();

		myReferenceSuffix = "Reference";

		sealAndInitializeChoiceTypes(theContext, theClassToElementDefinitions, mySpecializationChoiceTypes, true);
		sealAndInitializeChoiceTypes(theContext, theClassToElementDefinitions, myChoiceTypes, false);

		myNameToChildDefinition = Collections.unmodifiableMap(myNameToChildDefinition);
		myDatatypeToElementName = Collections.unmodifiableMap(myDatatypeToElementName);
		myDatatypeToElementDefinition = Collections.unmodifiableMap(myDatatypeToElementDefinition);
		myResourceTypes = Collections.unmodifiableList(myResourceTypes);
	}

	private void sealAndInitializeChoiceTypes(
			FhirContext theContext,
			Map<Class<? extends IBase>, BaseRuntimeElementDefinition<?>> theClassToElementDefinitions,
			List<Class<? extends IBase>> choiceTypes,
			boolean theIsSpecilization) {
		for (Class<? extends IBase> next : choiceTypes) {

			String elementName = null;
			BaseRuntimeElementDefinition<?> nextDef;
			boolean nonPreferred = false;
			if (IBaseResource.class.isAssignableFrom(next)) {
				elementName = getElementName() + StringUtils.capitalize(next.getSimpleName());
				nextDef = findResourceReferenceDefinition(theClassToElementDefinitions);

				if (!theIsSpecilization) {
					myNameToChildDefinition.put(getElementName() + "Reference", nextDef);
					myNameToChildDefinition.put(getElementName() + "Resource", nextDef);
				}

				myResourceTypes.add((Class<? extends IBaseResource>) next);

			} else {
				nextDef = theClassToElementDefinitions.get(next);
				BaseRuntimeElementDefinition<?> nextDefForChoice = nextDef;

				/*
				 * In HAPI 1.3 the following applied:
				 * Elements which are called foo[x] and have a choice which is a profiled datatype must use the
				 * unprofiled datatype as the element name. E.g. if foo[x] allows markdown as a datatype, it calls the
				 * element fooString when encoded, because markdown is a profile of string. This is according to the
				 * FHIR spec
				 *
				 * Note that as of HAPI 1.4 this applies only to non-primitive datatypes after discussion
				 * with Grahame.
				 */
				if (nextDef instanceof IRuntimeDatatypeDefinition) {
					IRuntimeDatatypeDefinition nextDefDatatype = (IRuntimeDatatypeDefinition) nextDef;
					if (nextDefDatatype.getProfileOf() != null && !IPrimitiveType.class.isAssignableFrom(next)) {
						nextDefForChoice = null;
						nonPreferred = true;
						Class<? extends IBaseDatatype> profileType = nextDefDatatype.getProfileOf();
						BaseRuntimeElementDefinition<?> elementDef = theClassToElementDefinitions.get(profileType);
						elementName = getElementName() + StringUtils.capitalize(elementDef.getName());
					}
				}
				if (nextDefForChoice != null) {
					elementName = getElementName() + StringUtils.capitalize(nextDefForChoice.getName());
				}
			}

			// I don't see how elementName could be null here, but eclipse complains..
			if (!theIsSpecilization) {
				if (elementName != null) {
					if (!myNameToChildDefinition.containsKey(elementName) || !nonPreferred) {
						myNameToChildDefinition.put(elementName, nextDef);
					}
				}

				/*
				 * If this is a resource reference, the element name is "fooNameReference"
				 */
				if (IBaseResource.class.isAssignableFrom(next) || IBaseReference.class.isAssignableFrom(next)) {
					next = theContext.getVersion().getResourceReferenceType();
					elementName = getElementName() + myReferenceSuffix;
					myNameToChildDefinition.put(elementName, nextDef);
				}
			}

			myDatatypeToElementDefinition.put(next, nextDef);

			if (myDatatypeToElementName.containsKey(next)) {
				String existing = myDatatypeToElementName.get(next);
				if (!existing.equals(elementName)) {
					throw new ConfigurationException(
							Msg.code(1693) + "Already have element name " + existing + " for datatype "
									+ next.getSimpleName() + " in " + getElementName() + ", cannot add " + elementName);
				}
			} else {
				myDatatypeToElementName.put(next, elementName);
			}
		}
	}

	public List<Class<? extends IBaseResource>> getResourceTypes() {
		return myResourceTypes;
	}

	@Override
	public String getChildNameByDatatype(Class<? extends IBase> theDatatype) {
		return myDatatypeToElementName.get(theDatatype);
	}

	@Override
	public BaseRuntimeElementDefinition<?> getChildElementDefinitionByDatatype(Class<? extends IBase> theDatatype) {
		return myDatatypeToElementDefinition.get(theDatatype);
	}

	public Set<Class<? extends IBase>> getValidChildTypes() {
		return Collections.unmodifiableSet((myDatatypeToElementDefinition.keySet()));
	}
}

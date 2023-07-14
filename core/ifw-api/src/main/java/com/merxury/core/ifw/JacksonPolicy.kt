/*
 * Copyright (c) 2020.
 *
 * This file is part of xmlutil.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.merxury.core.ifw

import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.OutputKind
import nl.adaptivity.xmlutil.serialization.XmlConfig
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.structure.SafeParentInfo

/**
 * Example policy that (very crudely) mimicks the way that Jackson serializes xml. It starts by eliding defaults.
 * Note that this version doesn't handle the jackson annotations.
 */
object JacksonPolicy :
    DefaultXmlSerializationPolicy(
        pedantic = false,
        encodeDefault = XmlSerializationPolicy.XmlEncodeDefault.NEVER,
    ) {
    /*
     * Rather than replacing the method wholesale, just make attributes into elements unless the [XmlElement] annotation
     * is present with a `false` value on the value attribute.
     */
    override fun effectiveOutputKind(
        serializerParent: SafeParentInfo,
        tagParent: SafeParentInfo,
        canBeAttribute: Boolean,
    ): OutputKind {
        val r = super.effectiveOutputKind(serializerParent, tagParent, canBeAttribute)
        return when {
            // Do take into account the XmlElement annotation
            r == OutputKind.Attribute &&
                serializerParent.elementUseAnnotations.mapNotNull { it as? XmlElement }
                    .firstOrNull()?.value != false ->
                OutputKind.Element

            else -> r
        }
    }

    /**
     * Jackson naming policy is based upon use name only. However, for this policy we do take the type annotation
     * if it is available. If there is no annotation for the name, we get the name out of the useName in all cases
     * (the default policy is dependent on member kind and the output used (attribute vs element)).
     */
    override fun effectiveName(
        serializerParent: SafeParentInfo,
        tagParent: SafeParentInfo,
        outputKind: OutputKind,
        useName: XmlSerializationPolicy.DeclaredNameInfo,
    ): QName {
        return useName.annotatedName
            ?: serializerParent.elementTypeDescriptor.typeQname
            ?: serialUseNameToQName(useName, tagParent.namespace)
    }
}

/** Extension function for elegant configuration */
fun XmlConfig.Builder.jacksonPolicy() {
    @OptIn(ExperimentalXmlUtilApi::class)
    policy = JacksonPolicy
}

/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.spdx.toSpdx

class PackageCurationDataTest : WordSpec({
    val original = PackageCurationData(
        comment = "original",
        purl = "original",
        cpe = "original",
        authors = setOf("original"),
        concludedLicense = "original".toSpdx(),
        description = "original",
        homepageUrl = "original",
        binaryArtifact = RemoteArtifact(
            url = "original",
            hash = Hash.create("original")
        ),
        sourceArtifact = RemoteArtifact(
            url = "original",
            hash = Hash.create("original")
        ),
        vcs = VcsInfoCurationData(
            type = VcsType.GIT,
            url = "original",
            revision = "original",
            path = "original"
        ),
        isMetadataOnly = true,
        isModified = true,
        declaredLicenseMapping = mapOf("original" to "LicenseRef-original".toSpdx()),
        sourceCodeOrigins = listOf(SourceCodeOrigin.ARTIFACT, SourceCodeOrigin.VCS),
        labels = mapOf(
            "k1" to "v1-original",
            "k2" to "v2-original"
        )
    )

    val other = PackageCurationData(
        comment = "other",
        purl = "other",
        cpe = "other",
        authors = setOf("other"),
        concludedLicense = "other".toSpdx(),
        description = "other",
        homepageUrl = "other",
        binaryArtifact = RemoteArtifact(
            url = "other",
            hash = Hash.create("other")
        ),
        sourceArtifact = RemoteArtifact(
            url = "other",
            hash = Hash.create("other")
        ),
        vcs = VcsInfoCurationData(
            type = VcsType.SUBVERSION,
            url = "other",
            revision = "other",
            path = "other"
        ),
        isMetadataOnly = false,
        isModified = false,
        declaredLicenseMapping = mapOf("other" to "LicenseRef-other".toSpdx()),
        sourceCodeOrigins = listOf(SourceCodeOrigin.VCS),
        labels = mapOf(
            "k2" to "v2-other",
            "k3" to "v3-other"
        )
    )

    "Merging" should {
        "replace all unset data" {
            PackageCurationData().merge(other) shouldBe other
        }

        "replace unset original data" {
            val originalWithSomeUnsetData = original.copy(
                comment = null,
                authors = null,
                concludedLicense = null,
                binaryArtifact = null,
                vcs = null,
                isMetadataOnly = null,
                declaredLicenseMapping = emptyMap(),
                sourceCodeOrigins = null,
                labels = emptyMap()
            )

            originalWithSomeUnsetData.merge(other) shouldBe originalWithSomeUnsetData.copy(
                comment = other.comment,
                authors = other.authors,
                concludedLicense = other.concludedLicense,
                binaryArtifact = other.binaryArtifact,
                vcs = other.vcs,
                isMetadataOnly = other.isMetadataOnly,
                declaredLicenseMapping = other.declaredLicenseMapping,
                sourceCodeOrigins = other.sourceCodeOrigins,
                labels = other.labels
            )
        }

        "keep existing original data" {
            original.merge(other) shouldBe original.copy(
                comment = "original\nother",
                authors = setOf("original", "other"),
                concludedLicense = "original AND other".toSpdx(),
                declaredLicenseMapping = mapOf(
                    "original" to "LicenseRef-original".toSpdx(),
                    "other" to "LicenseRef-other".toSpdx()
                ),
                labels = mapOf(
                    "k1" to "v1-original",
                    "k2" to "v2-other",
                    "k3" to "v3-other"
                )
            )
        }

        "not keep duplicate data" {
            val otherWithSomeOriginalData = other.copy(
                comment = original.comment,
                authors = original.authors,
                concludedLicense = original.concludedLicense,
                declaredLicenseMapping = original.declaredLicenseMapping,
                sourceCodeOrigins = original.sourceCodeOrigins,
                labels = original.labels
            )

            val mergedData = original.merge(otherWithSomeOriginalData)

            mergedData shouldBe original
            mergedData.concludedLicense.toString() shouldBe original.concludedLicense.toString()
            mergedData.declaredLicenseMapping.values.map { it.toString() } shouldBe original.declaredLicenseMapping
                .values.map { it.toString() }
        }

        "merge nested VCS information" {
            val originalWithPartialVcsData = original.copy(vcs = original.vcs?.copy(path = null))

            originalWithPartialVcsData.merge(other).vcs shouldBe original.vcs?.copy(path = other.vcs?.path)
        }
    }
})

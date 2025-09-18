/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.spyk

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.ResolvedConfiguration
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.PackageLicenseChoice
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.scannerRunOf

class RuleSetTest : WordSpec({
    val errorMessage = "error message"
    val howToFix = "how to fix"

    "package rule" should {
        "add errors if it has no requirements" {
            val ruleSet = ruleSet(ortResult) {
                packageRule("test") {
                    error(errorMessage, howToFix)
                }
            }

            ruleSet.violations should haveSize(allPackages.size + allProjects.size)
        }

        "add errors only if all requirements are met" {
            val ruleSet = ruleSet(ortResult) {
                packageRule("test") {
                    require {
                        +isExcluded()
                    }

                    error(errorMessage, howToFix)
                }
            }

            ruleSet.violations should haveSize(2)
        }

        "add license errors only if all requirements are met" {
            val ruleSet = ruleSet(ortResult) {
                packageRule("test") {
                    require {
                        -isExcluded()
                    }

                    licenseRule("test", LicenseView.ALL) {
                        require {
                            +isSpdxLicense()
                        }

                        error(errorMessage, howToFix)
                    }
                }
            }

            ruleSet.violations should haveSize(6)
        }

        "should not report violations for licenses from excluded paths when combined with license choices" {
            // This test reproduces the bug where excluded licenses are incorrectly considered when applying license choices
            val testPackageId = Identifier("Maven:test:package-with-excluded-license:1.0")

            val testOrtResult = ortResult.copy(
                repository = ortResult.repository.copy(
                    config = ortResult.repository.config.copy(
                        licenseChoices = LicenseChoices(
                            packageLicenseChoices = listOf(
                                PackageLicenseChoice(
                                    packageId = testPackageId,
                                    licenseChoices = listOf(
                                        SpdxLicenseChoice("MIT OR GPL-2.0-only".toSpdx(), "MIT".toSpdx())
                                    )
                                )
                            )
                        )
                    )
                ),
                analyzer = ortResult.analyzer!!.copy(
                    result = ortResult.analyzer!!.result.copy(
                        packages = ortResult.analyzer!!.result.packages + Package.EMPTY.copy(id = testPackageId)
                    )
                ),
                scanner = scannerRunOf(
                    testPackageId to listOf(
                        ScanResult(
                            provenance = UnknownProvenance,
                            scanner = ScannerDetails.EMPTY,
                            summary = ScanSummary.EMPTY.copy(
                                licenseFindings = setOf(
                                    // This finding should NOT be excluded - it's the compound license
                                    LicenseFinding("MIT OR GPL-2.0-only", TextLocation("LICENSE", 1)),
                                    // This finding SHOULD be excluded by path exclude
                                    LicenseFinding("GPL-2.0-only", TextLocation("excluded/LICENSE.GPL", 1))
                                )
                            )
                        )
                    )
                ),
                resolvedConfiguration = ResolvedConfiguration(
                    packageConfigurations = listOf(
                        PackageConfiguration(
                            id = testPackageId,
                            pathExcludes = listOf(
                                PathExclude(
                                    pattern = "excluded/**",
                                    reason = PathExcludeReason.BUILD_TOOL_OF,
                                    comment = "Excluded directory for testing"
                                )
                            )
                        )
                    )
                )
            )

            val ruleSet = ruleSet(testOrtResult) {
                packageRule("test-license-choice-with-excludes") {
                    require {
                        +isType("Maven")
                        +isFromOrg("test")
                    }

                    licenseRule("test", LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED) {
                        require {
                            -isExcluded()
                            +containsLicense("GPL-2.0-only".toSpdx())
                        }

                        error("GPL-2.0-only should be excluded by path exclude and license choice", "Remove GPL-2.0-only")
                    }
                }
            }

            // This test currently FAILS due to the bug - it finds a violation when it shouldn't
            // The bug is that filterExcluded() is not called before applyChoices() in PackageRule.licenseRule()
            ruleSet.violations should beEmpty()
        }
    }

    "dependency rule" should {
        "add errors if it has no requirements" {
            val ruleSet = ruleSet(ortResult) {
                dependencyRule("test") {
                    error(errorMessage, howToFix)
                }
            }

            ruleSet.violations should haveSize(allPackages.size)
        }

        "add errors only if all requirements are met" {
            val ruleSet = ruleSet(ortResult) {
                dependencyRule("test") {
                    require {
                        +isStaticallyLinked()
                    }

                    error(errorMessage, howToFix)
                }
            }

            ruleSet.violations should haveSize(1)
        }

        "add license errors only if all requirements are met" {
            val ruleSet = ruleSet(ortResult) {
                dependencyRule("test") {
                    require {
                        -isStaticallyLinked()
                    }

                    licenseRule("test", LicenseView.ALL) {
                        require {
                            +isSpdxLicense()
                        }

                        error(errorMessage, howToFix)
                    }
                }
            }

            ruleSet.violations should haveSize(6)
        }

        "add no license errors if license is removed by package license choice in the correct order" {
            val ruleSet = ruleSet(ortResult) {
                dependencyRule("test") {
                    licenseRule("test", LicenseView.ONLY_CONCLUDED) {
                        require {
                            +containsLicense("LicenseRef-b".toSpdx())
                        }

                        error(errorMessage, howToFix)
                    }
                }
            }

            ruleSet.violations should haveSize(1)
        }

        "add no license errors if license is removed by repository license choice" {
            val ruleSet = ruleSet(ortResult) {
                dependencyRule("test") {
                    licenseRule("test", LicenseView.ONLY_CONCLUDED) {
                        require {
                            +containsLicense("LicenseRef-c".toSpdx())
                        }

                        error(errorMessage, howToFix)
                    }
                }
            }

            ruleSet.violations should beEmpty()
        }


        "use stable references as ancestor nodes" {
            val result = spyk(ortResult)
            val navigator = spyk(ortResult.dependencyNavigator)
            every { result.dependencyNavigator } returns navigator

            every { navigator.directDependencies(any(), any()) } answers {
                ortResult.dependencyNavigator.directDependencies(firstArg(), secondArg()).map { node ->
                    val spyNode = spyk(node)
                    every { spyNode.getStableReference() } answers {
                        val ref = spyk(spyNode)
                        every { ref.id } answers { node.id.copy(name = node.id.name + "-ref") }
                        ref
                    }

                    spyNode
                }
            }

            val ruleSet = ruleSet(result) {
                dependencyRule("test") {
                    require {
                        -isStaticallyLinked()
                    }

                    if (ancestors.any { !it.id.name.endsWith("-ref") }) {
                        error("Node is not a reference.", howToFix)
                    }
                }
            }

            ruleSet.violations should beEmpty()
        }
    }
})

private fun PackageRule.LicenseRule.containsLicense(expression: SpdxExpression) =
    object : RuleMatcher {
        override val description = "containsLicense(license)"

        override fun matches() = license == expression
    }

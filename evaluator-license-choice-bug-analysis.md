# Evaluator License Choice and Path Exclude Bug Analysis

## Problem Description

When the ORT evaluator processes a package with:
- Multiple license findings: `Elastic OR SSPL` and `SSPL`
- A license choice defined: `Elastic OR SSPL -> Elastic`
- The file containing the sole `SSPL` finding excluded via path exclude

The evaluator incorrectly reports a finding for `SSPL`, even though it should be excluded by the combination of the license choice and path exclude.

## Root Cause

The issue is in the order of operations in the `licenseRule` function in `evaluator/src/main/kotlin/PackageRule.kt` (lines 234-242):

```kotlin
fun licenseRule(name: String, licenseView: LicenseView, block: LicenseRule.() -> Unit) {
    resolvedLicenseInfo.filter(licenseView, filterSources = true)
        .applyChoices(ruleSet.ortResult.getPackageLicenseChoices(pkg.metadata.id), licenseView)
        .applyChoices(ruleSet.ortResult.getRepositoryLicenseChoices(), licenseView).forEach { resolvedLicense ->
            // ...
        }
}
```

### Current Flow (Incorrect)
1. Filter by license view
2. Apply license choices
3. Check for exclusions in `LicenseRule.isExcluded()`

### The Problem
When `applyChoices()` is called, it uses the `effectiveLicense()` method which considers ALL licenses (including those that should be excluded by path excludes) when calculating which licenses remain after applying the choice. This can reintroduce licenses that were meant to be excluded.

## Why It Works in the Web App Report

The web app report (`plugins/reporters/evaluated-model/src/main/kotlin/EvaluatedModelMapper.kt`, line 274) does it correctly:

```kotlin
effectiveLicense = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded().effectiveLicense(
    LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
    input.ortResult.getPackageLicenseChoices(pkg.id),
    input.ortResult.getRepositoryLicenseChoices()
)?.sorted()
```

### Correct Flow
1. Get resolved license info
2. **Filter excluded licenses** via `filterExcluded()`
3. Apply license choices

The key difference is that `filterExcluded()` is called BEFORE applying license choices, which removes excluded licenses from consideration entirely.

## Detailed Scenario Analysis

Given:
- License findings: `Elastic OR SSPL` (not excluded) and `SSPL` (excluded)
- License choice: `Elastic OR SSPL -> Elastic`

### What happens in the evaluator:
1. Both licenses are present in the resolved license info
2. License choice is applied on all licenses
3. The `effectiveLicense` calculation sees both `Elastic OR SSPL` and `SSPL`
4. Even though the choice selects `Elastic`, the standalone `SSPL` might remain because it's considered separately
5. By the time `LicenseRule.isExcluded()` is checked, the excluded license has already been reintroduced

### What happens in the web app:
1. Both licenses are present in the resolved license info
2. `filterExcluded()` removes the excluded `SSPL` 
3. License choice is applied only on the remaining `Elastic OR SSPL`
4. The choice correctly results in only `Elastic`

## Solution

The evaluator should call `filterExcluded()` before applying license choices, similar to how the web app report handles it. This ensures that excluded licenses are not considered when applying license choices and cannot be reintroduced into the results.
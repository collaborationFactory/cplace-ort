---
name: Bug report
about: Create a report to help us improve
title: "Evaluator incorrectly processes license choices when path excludes are involved"
labels: bug
---

## Describe the bug

The ORT evaluator incorrectly reports license violations when a package has multiple license findings with both license choices and path excludes applied. This is due to the order of operations in the evaluator logic (apply choices, then remove excluded findings) which is the reverse of the order that reporters apply (filter excluded, then apply choices).

## To Reproduce

I have created a unit test reproducing the behavior: https://github.com/collaborationFactory/cplace-ort/commit/b13bacb3a331edc2e6e58c8f4ff91d1efad9c664

Steps to manually reproduce the behavior:

1. Set up a package with multiple license findings:
   - Finding 1: `MIT OR GPL-2.0-only` in a non-excluded file
   - Finding 2: `GPL-2.0-only` in an excluded directory path (`excluded/**`)

2. Configure a license choice: `MIT OR GPL-2.0-only -> MIT`

3. Configure path excludes to exclude files in the `excluded/**` directory

4. Run the ORT evaluator with a license rule using `-isExcluded()`

5. Observe that the evaluator incorrectly reports a violation for `GPL-2.0-only`, even though it should be excluded by the combination of the license choice and path exclude

## Expected behavior

The evaluator should:
1. Apply path excludes to remove excluded licenses from consideration
2. Apply license choices on the remaining licenses
3. Result in only `MIT` being reported (as the web app report correctly does)

## Console / log output

Depending on your rule set, the evaluator generates a violation. See referenced unit test for reproduction.

## Environment

- **ORT version**: 61.0.0 docker
- **ORT configuration**:

Repository configuration:
  ```yaml
  license_choices:
    repository_license_choices:
      license_choices:
        - given: "MIT OR GPL-2.0-only"
          choice: "MIT"
  ```

Package configuration:
  ```yaml
  excludes:
    paths:
      - pattern: "excluded/**"
        reason: "EXCLUDED"
  ```

## Additional context

I have already done some investigation into the root cause:

**Root Cause Analysis:**

The issue is in the order of operations. The current flow is:
1. Filter by license view
2. Apply license choices
3. Check for exclusions in `LicenseRule.isExcluded()`

**Correct Implementation Reference:**

The web app report handles this correctly in `plugins/reporters/evaluated-model/src/main/kotlin/EvaluatedModelMapper.kt` (line 274):

```kotlin
effectiveLicense = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded().effectiveLicense(
    LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
    input.ortResult.getPackageLicenseChoices(pkg.id),
    input.ortResult.getRepositoryLicenseChoices()
)?.sorted()
```

The key difference is that `filterExcluded()` is called BEFORE applying license choices.

**Attempted Solutions:**

1. **Global Fix Attempt** (https://github.com/collaborationFactory/cplace-ort/commit/bec9c8fedd5aa9a7969d3a8df88f7584f0af64db):
   - Added `.filterExcluded()` call in the `licenseRule` function before applying choices
   - This approach always filters excluded licenses
   - Unfortunately, this is not applicable as the decision to consider excluded paths should be made by the rules themselves by using the matcher `isExcluded()`

2. **Workaround** (https://github.com/collaborationFactory/cplace-ort/commit/42566606a7a5ade5c087730ccfc1ee6466f261c1):
   - Created a custom matcher `+isEffective()` instead of using the built-in `-isExcluded()`
   - The `isEffective()` method properly filters excluded licenses before applying choices
   - The custom matcher can be included in the custom rule set without modifying the core evaluator logic

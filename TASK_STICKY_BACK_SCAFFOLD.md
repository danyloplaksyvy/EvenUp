# Task: UI-004 Reusable Sticky Back Top Bar Scaffold

## Goal:
Create one reusable design-system scaffold for screens that use a collapsing top bar with an animated sticky back button, then migrate `ReceiptReviewScreen` to use it without changing feature behavior.

## Context:
`ReceiptReviewScreen` currently implements the sticky back button behavior locally: it creates a Material `enterAlwaysScrollBehavior`, attaches `nestedScroll`, renders `CenterAlignedTopAppBar`, calculates `showStickyBackButton`, animates `StickyBackButton`, and defines the sticky button inside the receipt review feature package.

This works visually, but it is not scalable. Other screens with similar top bars would need to duplicate the same scroll behavior, animation specs, threshold logic, status-bar padding, and accessibility semantics. The sticky back button should become a reusable design-system pattern, not a feature-local implementation.

The expected result is a reusable template that feature screens can adopt with minimal boilerplate:

```kotlin
EvenUpCollapsingTopBarScaffold(
    title = "Receipt review",
    onNavigationClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
    bottomBar = {
        if (!uiState.isLoading && !uiState.missingDraft) {
            ReceiptReviewBottomBar(uiState = uiState, onEvent = onEvent)
        }
    },
) { innerPadding ->
    // screen content
}
```

## Relevant files:
- `feature/expenseflow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewScreen.kt`
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/EvenUpTopBar.kt`
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/EvenUpTheme.kt`
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/EvenUpBottomActionBar.kt`

## Scope:

### You may edit:
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/`
- `feature/expenseflow/impl/src/main/kotlin/com/dps/evenup/feature/expenseflow/impl/receiptreview/ReceiptReviewScreen.kt`

### You may create:
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/EvenUpStickyBackButton.kt`
- `core/designsystem/api/src/main/kotlin/com/dps/evenup/core/designsystem/api/EvenUpCollapsingTopBarScaffold.kt`

### Do not edit:
- Domain modules
- Data modules
- DTOs, database entities, or persistence code
- Receipt review reducer/ViewModel/business logic
- Navigation graph unless required by compilation
- Assign-items screen in this task
- New-expense screen in this task

## Architecture constraints:
- Keep API/implementation separation.
- Feature modules must not depend on data impl modules.
- Domain modules must not depend on Android UI, Compose, DTOs, or database entities.
- Do not use `Float` or `Double` for money.
- UI-only animation thresholds may use `Float`; money and financial calculations must not.
- The design-system API must not depend on feature modules.
- The reusable scaffold must expose generic callbacks and slots, not feature-specific events or state.
- Do not pass `ReceiptReviewUiState`, `ReceiptReviewUiEvent`, or any feature-specific model into design-system components.

## Implementation requirements:

### 1. Extract `EvenUpStickyBackButton`
Create a reusable composable in the design-system API module.

Suggested signature:

```kotlin
@Composable
fun EvenUpStickyBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Navigate back",
)
```

Requirements:
- Use the existing visual style from `ReceiptReviewScreen`:
  - circular/avatar shape
  - `EvenUpTheme.colors.background`
  - `EvenUpTheme.colors.textPrimary`
  - border using `EvenUpTheme.colors.border`
  - size `48.dp`
  - status-bar padding
  - top/start padding from theme spacing
- Use `Icons.AutoMirrored.Filled.ArrowBack`.
- Preserve accessibility semantics:
  - role button
  - meaningful content description
- Use a proper clickable button implementation where possible.
  - Prefer `Surface(onClick = ...)` if available in the current Material3 version.
  - If using `Modifier.clickable`, keep `role = Role.Button` semantics.
- Do not make this component receipt-specific.

### 2. Create `EvenUpCollapsingTopBarScaffold`
Create a reusable design-system scaffold for screens with a collapsing top bar and animated sticky back button.

Suggested signature:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvenUpCollapsingTopBarScaffold(
    title: String,
    onNavigationClick: () -> Unit,
    modifier: Modifier = Modifier,
    navigationContentDescription: String = "Navigate back",
    showStickyNavigationButton: Boolean = true,
    stickyNavigationThreshold: Float = 0.85f,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
)
```

Requirements:
- Internally create `TopAppBarDefaults.enterAlwaysScrollBehavior()`.
- Apply `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` to the root scaffold modifier.
- Use `Scaffold` with `EvenUpTheme.colors.background` as the container color.
- Use `CenterAlignedTopAppBar` or existing `EvenUpTopBar` only if it supports `scrollBehavior`.
- Keep visual parity with the current receipt review top bar:
  - centered title
  - `EvenUpTheme.typography.sectionTitle`
  - `EvenUpTheme.colors.textPrimary`
  - background/scrolled background from `EvenUpTheme.colors.background`
- Keep the normal navigation icon in the top bar.
- Overlay `EvenUpStickyBackButton` inside the scaffold content using `Box`.
- Show sticky button when:

```kotlin
showStickyNavigationButton && scrollBehavior.state.collapsedFraction > stickyNavigationThreshold
```

- Use `remember` + `derivedStateOf` for the visibility calculation.
- Clamp or validate the threshold defensively:
  - If the parameter is outside `0f..1f`, coerce it into the valid range.
- Use the same animation pattern currently used in `ReceiptReviewScreen`:
  - enter: `fadeIn` + `scaleIn` + `slideInVertically`
  - exit: `fadeOut` + `scaleOut` + `slideOutVertically`
  - enter duration about `180ms`
  - exit duration about `120ms`
  - `FastOutSlowInEasing`
- Do not force the screen content to use `verticalScroll` or `LazyColumn`.
  - The scaffold should only provide `innerPadding`.
  - The feature screen decides whether to use `Column.verticalScroll`, `LazyColumn`, etc.

### 3. Migrate `ReceiptReviewScreen`
Update `ReceiptReviewScreen` to use `EvenUpCollapsingTopBarScaffold`.

Current local responsibilities that should be removed from `ReceiptReviewScreen`:
- local `TopAppBarDefaults.enterAlwaysScrollBehavior()`
- local `nestedScroll(...)`
- local `CenterAlignedTopAppBar`
- local `AnimatedVisibility` sticky button block
- private `StickyBackButton` composable
- animation imports that become unused
- top-app-bar imports that become unused
- status-bar/back-button-specific imports that become unused

The screen should keep:
- loading state behavior
- missing draft behavior
- receipt content behavior
- bottom bar behavior
- edit sheet behavior
- all existing events
- all existing validation rendering

Target structure:

```kotlin
//@Composable
//fun ReceiptReviewScreen(
//    uiState: ReceiptReviewUiState,
//    onEvent: (ReceiptReviewUiEvent) -> Unit,
//    modifier: Modifier = Modifier,
//) {
//    EvenUpCollapsingTopBarScaffold(
//        title = "Receipt review",
//        onNavigationClick = { onEvent(ReceiptReviewUiEvent.BackClick) },
//        modifier = modifier.fillMaxSize(),
//        bottomBar = {
//            if (!uiState.isLoading && !uiState.missingDraft) {
//                ReceiptReviewBottomBar(uiState = uiState, onEvent = onEvent)
//            }
//        },
//    ) { innerPadding ->
//        when {
//            uiState.isLoading -> EvenUpLoadingState(...)
//            uiState.missingDraft -> EvenUpErrorState(...)
//            else -> ReceiptReviewContent(...)
//        }
//    }
//}
```

Use a `Box` inside the scaffold only if the current loading/error/content layout requires it. Do not add unnecessary wrapper layers.

### 4. Avoid future duplication
Add a short KDoc comment to `EvenUpCollapsingTopBarScaffold` explaining when to use it:
- use for screens with a Material top bar that should collapse while scrolling
- use when the back action should remain reachable after the top bar collapses
- do not use for screens with custom sticky business headers, such as assignment headers, unless they only need the generic top-bar behavior

### 5. Keep the assign-items sticky header separate
Do not merge assign-items sticky participant selector into this scaffold.

Reason:
- assign-items needs a sticky business/action header, not only a sticky navigation affordance
- that should be a separate future component or screen-level implementation

Possible future task:

```text
Create EvenUpStickyHeaderScaffold for screens with custom sticky content below the top bar.
```

## Validation:
Run:

```bash
./gradlew :core:designsystem:api:compileDebugKotlin
./gradlew :feature:expenseflow:impl:compileDebugKotlin
./gradlew ktlintCheck
```

If module names differ, inspect `settings.gradle.kts` and run the closest equivalent module-level Kotlin compile tasks.

Also run the app and manually verify:
- Open receipt review screen.
- Scroll down until the top bar collapses.
- Sticky back button appears with fade/scale/slide animation.
- Scroll back to the top.
- Sticky back button disappears smoothly.
- The normal top-bar back button still works.
- The sticky back button still works.
- Loading state still renders correctly.
- Missing draft error state still renders correctly.
- Bottom action bar still renders correctly when expected.
- Edit bottom sheets still open and dismiss correctly.

## Done when:
- `EvenUpStickyBackButton` exists in the design-system API module.
- `EvenUpCollapsingTopBarScaffold` exists in the design-system API module.
- `ReceiptReviewScreen` uses `EvenUpCollapsingTopBarScaffold` instead of local top-bar/sticky-back implementation.
- The private `StickyBackButton` is removed from `ReceiptReviewScreen.kt`.
- No feature-specific type leaks into the design-system API.
- No duplicate sticky-back animation code remains in `ReceiptReviewScreen.kt`.
- The sticky back button appears at a threshold around `0.85f`, not only at exact full collapse.
- Existing receipt review behavior remains unchanged except for the reusable implementation detail.
- Kotlin compile passes for affected modules.
- Lint/formatting passes.

## Notes for implementation quality:
- Prefer simple slot-based APIs over over-configurable abstractions.
- Do not create a generic mega-scaffold that tries to support every future sticky header use case.
- Keep the first reusable scaffold focused on the proven receipt-review behavior.
- Keep animation specs centralized inside the design-system component.
- Keep feature screens responsible for their own content scrolling strategy.
- Avoid exposing `TopAppBarScrollBehavior` from feature screens unless there is a strong reason.

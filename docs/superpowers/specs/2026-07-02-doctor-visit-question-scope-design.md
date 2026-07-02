# Doctor Visit Question Scope Design

## Goal

Keep question ownership clear: the dashboard shows only unassigned questions, while an existing visit shows only questions assigned to that visit plus questions created during the current edit. Allow questions to be created directly from the visit form.

## Behavior

- The dashboard continues observing inbox questions (`visit_id IS NULL`) only.
- A new visit shows inbox questions so the user can select questions to attach.
- An existing visit shows its attached questions and any questions created during the current edit. It does not show unrelated inbox questions.
- The visit form includes the same text-field-and-add-button interaction used by the dashboard.
- A question created from the visit form is inserted into the inbox and immediately selected in the form.
- Saving the visit attaches selected questions through the existing atomic visit save operation.
- Canceling leaves newly created questions unassigned, so no entered question is lost and the visit is unchanged.
- “Manage questions” and “Manage all” become compact outlined buttons while retaining their current labels and navigation.

## Implementation

Reuse `AddVisitQuestionUseCase`; extend `DoctorVisitViewModel` with a draft and an add event. After insertion returns the question ID, add that ID to `selectedQuestionIds`. The existing inbox flow supplies the inserted question to the UI.

In `DoctorVisitSheet`, choose the displayed list from existing state:

- add mode: all inbox questions;
- edit mode: attached questions plus inbox questions whose IDs are selected during this edit.

No schema, DAO, repository, navigation, or new abstraction is required. The dashboard query already has the required inbox-only behavior.

## Error and Edge Handling

- Ignore blank input, matching the dashboard.
- Clear the draft before launching the insert to prevent rapid duplicate submissions.
- Keep the draft unchanged only when no submission occurs.
- Existing save and persistence error behavior remains unchanged.

## Verification

- ViewModel test: adding trims the question, clears the draft, and selects the returned ID.
- Compose tests: add mode exposes inbox questions; edit mode hides unrelated inbox questions while showing attached and newly selected inbox questions.
- Compose tests identify both management actions as buttons.
- Run focused doctor-visit tests, then the full test suite and build before completion.

# Issue tracker: Plane

Issues and PRDs for this repo live as **Plane work items** in the `aka-enterprise` workspace. All operations go through the **`plane` MCP server** (`mcp.plane.so`).

The Plane MCP tools are deferred — load them on demand with `ToolSearch` (e.g. query `plane`) before calling. If the server reports it needs auth, re-authenticate the `plane` MCP first.

## Conventions

Map each skill operation to the corresponding Plane MCP tool (create / read / list / update / comment on work items):

- **Create a work item**: create-work-item tool — pass title + Markdown description, and the target project.
- **Read a work item**: get-work-item tool by ID, including comments.
- **List work items**: list-work-items tool, filtered by project, state, and label.
- **Comment on a work item**: create-comment tool against the work-item ID.
- **Apply / remove labels**: update-work-item tool, setting the item's labels.
- **Change state**: update-work-item tool, setting the item's state (Plane states carry the triage roles — see `triage-labels.md`).

Resolve project / label / state IDs by listing them first (list-projects, list-labels, list-states) rather than hard-coding IDs.

## Pull requests as a triage surface

**PRs as a request surface: no.** Plane work items are the only request surface; code review happens separately on GitHub PRs and is not pulled into the triage queue.

## When a skill says "publish to the issue tracker"

Create a Plane work item in the relevant project.

## When a skill says "fetch the relevant ticket"

Read the Plane work item by ID (get-work-item, with comments).

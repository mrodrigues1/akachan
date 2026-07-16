# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues on `mrodrigues1/akachan`. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..." --project "akachan"`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

Infer the repo from `git remote -v` — `gh` does this automatically when run inside a clone.

## Workflow-state labels

Beyond the triage roles (`docs/agents/triage-labels.md`), two labels track active development state. Agents move issues through them:

- `in-progress` — an agent or human is actively working on this. Apply when starting work (branch created); remove `ready-for-agent` at the same time.
- `in-review` — implementation done; awaiting PR review/merge. Swap `in-progress` → `in-review` when the branch is complete and ready for a PR.

On merge, close the issue (labels stay for history; no "done" label — closed is the done state).

```bash
gh issue edit <number> --add-label "in-progress" --remove-label "ready-for-agent"   # start
gh issue edit <number> --add-label "in-review" --remove-label "in-progress"        # ready for PR
gh issue close <number> --comment "..."                                            # merged
```

Move the issues on the project board to the next column: Backlog (Created) -> Todo (Planned) -> In Progress (Work started) -> In Review (Pr open) -> Done (Pr Closed)

## Epics and sub-issues

Epics (label `epic`) link their children via GitHub's **native sub-issue relationships** — never a markdown `- [ ] #N` checklist in the epic body. Native links give the epic an auto-tracking progress panel and parent links on each child; a body checklist tracks nothing.

```bash
id=$(gh api repos/{owner}/{repo}/issues/<CHILD_NUMBER> --jq .id)   # numeric ID, not the issue number
gh api -X POST repos/{owner}/{repo}/issues/<EPIC_NUMBER>/sub_issues -F sub_issue_id=$id
```

Verify with `--jq .sub_issues_summary` on the epic. In the body, at most a plain note on implementation order.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.

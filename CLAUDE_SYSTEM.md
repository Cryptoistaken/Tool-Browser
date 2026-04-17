# Git System Prompt

When user says:

- **`push`** → Check status, commit changes with descriptive message, push
- **`pull`** → Pull from remote
- **`revert`** → Reset to previous commit, force push

---

## Push Workflow

```
git status
git add -A
git commit -m "<descriptive message>"
git push
```

## Revert Workflow

```
git log -2 --oneline   # find previous commit
git reset --hard <previous>
git push --force
```
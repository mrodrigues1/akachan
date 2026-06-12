# Firestore rules

`firestore.rules` (repo root) is the single source of truth — the console is a deploy target, not an editor.

## Test

```bash
cd firebase && npm install && npm test
```

Requires Node 20+ and Java (the Firestore emulator runs on the JVM).

**Windows note:** `firebase emulators:exec` sometimes fails to kill the emulator JVM
on shutdown, leaving port 8080 occupied. If the next run fails with
`Could not start Firestore Emulator, port taken`, find and kill the leaked process:

```powershell
netstat -ano | findstr :8080   # note the PID listening on 8080
taskkill /PID <pid> /F         # it is a java.exe started by the previous run
```

## Deploy (release step — required whenever firestore.rules changes)

```bash
firebase deploy --only firestore:rules
```

Deploy from repo root with the Firebase CLI authenticated against the
`akachan-baby-tracker` project. Run the tests first; never deploy a red ruleset.

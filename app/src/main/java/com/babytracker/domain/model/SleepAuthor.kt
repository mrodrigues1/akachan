package com.babytracker.domain.model

// Who started a sleep session. Dedicated to the sleep domain (kept separate from FeedAuthor)
// so the two partner-sharing features stay decoupled. Owner = the primary device's user.
enum class SleepAuthor { OWNER, PARTNER }

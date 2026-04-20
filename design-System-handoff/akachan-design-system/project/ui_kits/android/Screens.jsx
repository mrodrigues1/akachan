// Akachan UI Kit — Core Screen Components
// Theme colors from Color.kt / Theme.kt

const AK = {
  primary: '#C2185B',
  onPrimary: '#FFFFFF',
  primaryContainer: '#F8BBD0',
  onPrimaryContainer: '#880E4F',
  secondary: '#1976D2',
  onSecondary: '#FFFFFF',
  secondaryContainer: '#B3E5FC',
  onSecondaryContainer: '#0D47A1',
  tertiary: '#388E3C',
  tertiaryContainer: '#C8E6C9',
  onTertiaryContainer: '#1B5E20',
  surface: '#FFFDE7',
  onSurface: '#1A1A1A',
  onSurfaceVariant: '#757575',
  surfaceVariant: '#F0EDE0',
  outline: '#CAC4D0',
  errorContainer: '#FFDAD6',
  onErrorContainer: '#410002',
};

const AKDark = {
  primary: '#F48FB1',
  onPrimary: '#880E4F',
  primaryContainer: '#880E4F',
  onPrimaryContainer: '#F8BBD0',
  secondary: '#90CAF9',
  onSecondary: '#0D47A1',
  secondaryContainer: '#0D47A1',
  onSecondaryContainer: '#B3E5FC',
  tertiary: '#A5D6A7',
  tertiaryContainer: '#1B5E20',
  onTertiaryContainer: '#C8E6C9',
  surface: '#1C1B1F',
  onSurface: '#E6E1E5',
  onSurfaceVariant: '#CAC4D0',
  surfaceVariant: '#2B2930',
  outline: '#49454F',
};

const R = {
  xs: 4, sm: 8, md: 16, lg: 24, xl: 50,
};

const T = {
  display: { fontSize: 36, fontWeight: 800, letterSpacing: -1 },
  headlineLg: { fontSize: 32, fontWeight: 700 },
  titleLg: { fontSize: 22, fontWeight: 600 },
  titleMd: { fontSize: 18, fontWeight: 600 },
  titleSm: { fontSize: 14, fontWeight: 600 },
  bodyLg: { fontSize: 16, fontWeight: 400, letterSpacing: 0.5 },
  bodyMd: { fontSize: 14, fontWeight: 400 },
  bodySm: { fontSize: 12, fontWeight: 400 },
  labelMd: { fontSize: 12, fontWeight: 700, letterSpacing: 0.8, textTransform: 'uppercase' },
  labelSm: { fontSize: 11, fontWeight: 500, letterSpacing: 0.5 },
};

// ── Primitive helpers ─────────────────────────────────────────

function Card({ children, style, onClick, color, elevation = 2 }) {
  const c = color || AK.surface;
  return (
    <div onClick={onClick} style={{
      background: c, borderRadius: R.lg, padding: 20,
      boxShadow: `0 ${elevation}px ${elevation * 3}px rgba(0,0,0,0.10)`,
      cursor: onClick ? 'pointer' : 'default',
      ...style,
    }}>{children}</div>
  );
}

function OutlinedCardBtn({ children, style, onClick, color }) {
  return (
    <div onClick={onClick} style={{
      border: `1.5px solid ${AK.outline}`, borderRadius: R.md,
      padding: '14px 20px', cursor: 'pointer', display: 'flex',
      alignItems: 'center', justifyContent: 'center', gap: 8,
      background: color || 'transparent', ...style,
    }}>{children}</div>
  );
}

function PillBtn({ children, style, onClick, color, textColor, disabled }) {
  const bg = disabled ? 'rgba(26,26,26,0.12)' : (color || AK.primary);
  const tc = disabled ? 'rgba(26,26,26,0.38)' : (textColor || AK.onPrimary);
  return (
    <div onClick={!disabled ? onClick : undefined} style={{
      background: bg, color: tc,
      borderRadius: R.xl, padding: '16px 24px',
      cursor: disabled ? 'default' : 'pointer',
      textAlign: 'center', fontWeight: 600, fontSize: 14,
      letterSpacing: 0.1, lineHeight: '20px',
      transition: 'background 0.15s',
      ...style,
    }}>{children}</div>
  );
}

function SectionLabel({ children, color }) {
  return (
    <div style={{ ...T.labelMd, color: color || AK.primary, marginBottom: 6 }}>
      {children}
    </div>
  );
}

function Divider() {
  return <div style={{ height: 1, background: AK.outline, opacity: 0.3, margin: '0 16px' }} />;
}

function TopBar({ title, subtitle, onBack, onAction, actionLabel, dark }) {
  const col = dark ? AKDark : AK;
  return (
    <div style={{ background: col.surface, padding: '8px 16px 12px', display: 'flex', alignItems: 'flex-start', gap: 8 }}>
      {onBack && (
        <div onClick={onBack} style={{ padding: '4px 8px 0 0', cursor: 'pointer', color: col.onSurface, fontSize: 20 }}>←</div>
      )}
      <div style={{ flex: 1 }}>
        <div style={{ ...T.titleLg, color: col.onSurface }}>{title}</div>
        {subtitle && <div style={{ ...T.bodySm, color: col.onSurfaceVariant, marginTop: 2 }}>{subtitle}</div>}
      </div>
      {onAction && (
        <div onClick={onAction} style={{ ...T.titleSm, color: AK.primary, cursor: 'pointer', paddingTop: 4 }}>{actionLabel}</div>
      )}
    </div>
  );
}

function HistoryCard({ emoji, badgeColor, title, subtitle, trailing, trailColor }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0' }}>
      <div style={{ width: 44, height: 44, borderRadius: R.sm, background: badgeColor, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, flexShrink: 0 }}>{emoji}</div>
      <div style={{ flex: 1 }}>
        <div style={{ ...T.titleSm, color: AK.onSurface }}>{title}</div>
        <div style={{ ...T.bodySm, color: AK.onSurfaceVariant, marginTop: 2 }}>{subtitle}</div>
      </div>
      <div style={{ ...T.titleSm, color: trailColor || AK.primary }}>{trailing}</div>
    </div>
  );
}

// ── Ring Timer ────────────────────────────────────────────────

function RingTimer({ elapsed, maxSecs, color, trackColor, label }) {
  const canvasRef = React.useRef(null);
  const progress = maxSecs > 0 ? Math.min(elapsed / maxSecs, 1) : 0;
  const isOver = maxSecs > 0 && elapsed >= maxSecs;
  const ringColor = isOver ? AK.tertiary : (color || AK.primary);
  const trkColor = isOver ? AK.tertiaryContainer : (trackColor || AK.primaryContainer);

  React.useEffect(() => {
    const c = canvasRef.current;
    if (!c) return;
    const ctx = c.getContext('2d');
    const cx = 90, cy = 90, r = 74, sw = 14;
    ctx.clearRect(0, 0, 180, 180);
    ctx.beginPath(); ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.strokeStyle = trkColor; ctx.lineWidth = sw; ctx.lineCap = 'round'; ctx.stroke();
    if (progress > 0) {
      ctx.beginPath(); ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * progress);
      ctx.strokeStyle = ringColor; ctx.lineWidth = sw; ctx.lineCap = 'round'; ctx.stroke();
    }
  }, [elapsed, maxSecs]);

  const mins = Math.floor(elapsed / 60), secs = elapsed % 60;
  const timeStr = `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
  const pct = maxSecs > 0 ? `${Math.round(progress * 100)}% of ${maxSecs / 60}m` : '';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
      <div style={{ position: 'relative', width: 180, height: 180 }}>
        <canvas ref={canvasRef} width={180} height={180} />
        <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', textAlign: 'center' }}>
          <div style={{ ...T.display, color: isOver ? AK.tertiary : AK.onSurface }}>{timeStr}</div>
          {pct && <div style={{ ...T.labelSm, color: AK.onSurfaceVariant, marginTop: 4 }}>{pct}</div>}
        </div>
      </div>
      {label && <div style={{ ...T.labelMd, color: ringColor }}>{label}</div>}
    </div>
  );
}

// ── Side Selector ─────────────────────────────────────────────

function SideSelector({ selected, onSelect }) {
  return (
    <div style={{ display: 'flex', gap: 12, width: '100%' }}>
      {[{id:'LEFT', arrow:'←', label:'Left'}, {id:'RIGHT', arrow:'→', label:'Right'}].map(({ id, arrow, label }) => {
        const sel = selected === id;
        return (
          <div key={id} onClick={() => onSelect(id)} style={{
            flex: 1, height: 88, borderRadius: R.md, cursor: 'pointer',
            background: sel ? AK.primary : AK.surface,
            border: sel ? 'none' : `2px solid ${AK.primaryContainer}`,
            boxShadow: sel ? '0 6px 12px rgba(194,24,91,0.25)' : 'none',
            display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: 2,
            transition: 'all 0.15s',
          }}>
            <div style={{
              fontSize: 28, fontWeight: 400, lineHeight: 1,
              color: sel ? AK.onPrimary : `rgba(26,26,26,0.4)`,
            }}>{arrow}</div>
            <div style={{
              fontSize: 14, fontWeight: 700, letterSpacing: 0.1,
              color: sel ? AK.onPrimary : `rgba(26,26,26,0.6)`,
            }}>{label}</div>
          </div>
        );
      })}
    </div>
  );
}

// ── Bottom Navigation ─────────────────────────────────────────

function BottomNav({ active, onNav, dark }) {
  const col = dark ? AKDark : AK;
  const tabs = [
    { id: 'home', icon: '⊙', label: 'Home' },
    { id: 'feeding', icon: '🍼', label: 'Feeding' },
    { id: 'sleep', icon: '🌙', label: 'Sleep' },
    { id: 'settings', icon: '⚙', label: 'Settings' },
  ];
  return (
    <div style={{ background: col.surface, display: 'flex', borderTop: `1px solid ${col.outline}20` }}>
      {tabs.map(t => {
        const isActive = active === t.id;
        return (
          <div key={t.id} onClick={() => onNav(t.id)} style={{
            flex: 1, padding: '10px 0 8px', textAlign: 'center', cursor: 'pointer',
          }}>
            <div style={{ fontSize: 22, marginBottom: 2 }}>{t.icon}</div>
            <div style={{ ...T.labelSm, color: isActive ? col.primary : col.onSurfaceVariant }}>
              {t.label}
            </div>
            {isActive && <div style={{ width: 32, height: 3, background: isActive ? col.primary : 'transparent', borderRadius: 2, margin: '3px auto 0' }} />}
          </div>
        );
      })}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════════
// SCREENS
// ═══════════════════════════════════════════════════════════════

function HomeScreen({ babyName, onNav, lastFeedAgo, lastSleepDuration, tipSide, dark }) {
  const col = dark ? AKDark : AK;
  const sf = dark ? AKDark.surface : AK.surface;
  return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Top bar */}
      <div style={{ background: col.surface, padding: '8px 16px 4px' }}>
        <div style={{ ...T.titleLg, color: col.onSurface }}>Hi, {babyName} 👋</div>
        <div style={{ ...T.bodySm, color: col.onSurfaceVariant }}>Saturday, Apr 19</div>
      </div>
      {/* Content */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        {/* Summary cards */}
        <div style={{ display: 'flex', gap: 12 }}>
          <Card onClick={() => onNav('feeding')} style={{ flex: 1, padding: 20, cursor: 'pointer', background: col.surface }} color={col.surface}>
            <div style={{ fontSize: 24, marginBottom: 10 }}>🍼</div>
            <div style={{ ...T.titleSm, color: col.primary, fontWeight: 700 }}>Breastfeeding</div>
            <div style={{ ...T.bodyMd, color: col.primary, fontWeight: 600, marginTop: 4 }}>{lastFeedAgo}</div>
          </Card>
          <Card onClick={() => onNav('sleep')} style={{ flex: 1, padding: 20, cursor: 'pointer', background: col.surface }} color={col.surface}>
            <div style={{ fontSize: 24, marginBottom: 10 }}>🌙</div>
            <div style={{ ...T.titleSm, color: col.secondary, fontWeight: 700 }}>Sleep</div>
            <div style={{ ...T.bodyMd, color: col.secondary, fontWeight: 600, marginTop: 4 }}>{lastSleepDuration}</div>
          </Card>
        </div>
        {/* Tip card */}
        {tipSide && (
          <div style={{ background: col.primaryContainer, borderRadius: R.lg, padding: 14, display: 'flex', alignItems: 'flex-start', gap: 10 }}>
            <span style={{ fontSize: 18 }}>✨</span>
            <div>
              <div style={{ ...T.labelMd, color: col.onPrimaryContainer }}>Tip</div>
              <div style={{ ...T.bodySm, color: col.onPrimaryContainer, marginTop: 2 }}>
                Try {tipSide} breast next — used less last session.
              </div>
            </div>
          </div>
        )}
      </div>
      <BottomNav active="home" onNav={onNav} dark={dark} />
    </div>
  );
}

function BreastfeedingScreen({ onNav, dark }) {
  const col = dark ? AKDark : AK;
  const [phase, setPhase] = React.useState('idle'); // idle | active | paused
  const [selectedSide, setSelectedSide] = React.useState(null);
  const [elapsed, setElapsed] = React.useState(0);
  const [leftSecs, setLeftSecs] = React.useState(0);
  const [rightSecs, setRightSecs] = React.useState(0);
  const [currentSide, setCurrentSide] = React.useState('LEFT');
  const [switched, setSwitched] = React.useState(false);

  React.useEffect(() => {
    if (phase !== 'active') return;
    const iv = setInterval(() => {
      setElapsed(e => e + 1);
      if (currentSide === 'LEFT') setLeftSecs(s => s + 1);
      else setRightSecs(s => s + 1);
    }, 1000);
    return () => clearInterval(iv);
  }, [phase, currentSide]);

  const fmt = s => `${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;

  return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <TopBar title="Breastfeeding" onBack={() => onNav('home')} onAction={() => {}} actionLabel="History" dark={dark} />
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
        {phase === 'idle' ? (
          <>
            <div style={{ height: 40 }} />
            <div style={{ ...T.titleLg, color: col.onSurface }}>Start a feeding session</div>
            <div style={{ height: 8 }} />
            <SideSelector selected={selectedSide} onSelect={setSelectedSide} />
            <PillBtn
              disabled={!selectedSide}
              style={{ width: '100%', marginTop: 8 }}
              onClick={() => { if (selectedSide) { setCurrentSide(selectedSide); setPhase('active'); } }}
            >
              Start Session
            </PillBtn>
            {/* Last feeding summary */}
            <Card style={{ width: '100%', padding: 16 }} color={col.surface}>
              <SectionLabel>Last Feeding</SectionLabel>
              <div style={{ ...T.titleMd, color: col.onSurface, fontWeight: 700 }}>2h 30m ago</div>
              <div style={{ height: 12 }} />
              <div style={{ background: col.primaryContainer, borderRadius: R.sm, padding: '8px 12px', ...T.bodyMd, color: col.onPrimaryContainer }}>
                Start with: Left breast
              </div>
            </Card>
          </>
        ) : (
          <>
            {/* Status pill */}
            <div style={{
              background: phase === 'paused' ? col.secondaryContainer : col.primaryContainer,
              borderRadius: R.xl, padding: '6px 16px', display: 'flex', alignItems: 'center', gap: 6, marginTop: 8,
            }}>
              <span style={{ ...T.labelMd, color: phase === 'paused' ? col.onSecondaryContainer : col.onPrimaryContainer }}>
                {phase === 'paused' ? '⏸ Session paused' : '● Session in progress'}
              </span>
            </div>
            {/* Ring timer */}
            <RingTimer elapsed={elapsed} maxSecs={900} color={col.primary} trackColor={col.primaryContainer} />
            {/* Side breakdown */}
            <div style={{ display: 'flex', gap: 10, width: '100%' }}>
              {['LEFT', 'RIGHT'].map(side => {
                const isCurrent = side === currentSide;
                const dur = side === 'LEFT' ? leftSecs : rightSecs;
                return (
                  <div key={side} style={{
                    flex: 1, borderRadius: R.md, padding: '12px', textAlign: 'center',
                    background: isCurrent ? col.primaryContainer : col.surfaceVariant,
                  }}>
                    <div style={{ ...T.labelMd, color: isCurrent ? col.onPrimaryContainer : col.onSurfaceVariant }}>
                      {isCurrent ? '● ' : ''}{side === 'LEFT' ? 'LEFT' : 'RIGHT'}
                    </div>
                    <div style={{ ...T.titleMd, color: isCurrent ? col.onPrimaryContainer : col.onSurfaceVariant, marginTop: 4 }}>
                      {fmt(dur)}
                    </div>
                  </div>
                );
              })}
            </div>
            {/* Switch side */}
            {!switched && phase === 'active' && (
              <OutlinedCardBtn style={{ width: '100%' }} onClick={() => { setSwitched(true); setCurrentSide(s => s === 'LEFT' ? 'RIGHT' : 'LEFT'); }}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill={col.primary}><path d="M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z"/></svg>
                <span style={{ ...T.titleSm, color: col.primary }}>Switch to {currentSide === 'LEFT' ? 'Right' : 'Left'}</span>
              </OutlinedCardBtn>
            )}
            {/* Pause / Resume */}
            <OutlinedCardBtn style={{ width: '100%' }} onClick={() => setPhase(p => p === 'active' ? 'paused' : 'active')}>
              {phase === 'active'
                ? <svg width="20" height="20" viewBox="0 0 24 24" fill={col.primary}><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
                : <svg width="20" height="20" viewBox="0 0 24 24" fill={col.primary}><path d="M8 5v14l11-7z"/></svg>
              }
              <span style={{ ...T.titleSm, color: col.primary }}>{phase === 'active' ? 'Pause Session' : 'Resume Session'}</span>
            </OutlinedCardBtn>
            {/* Stop */}
            <OutlinedCardBtn color={col.primaryContainer} style={{ width: '100%' }} onClick={() => { setPhase('idle'); setElapsed(0); setLeftSecs(0); setRightSecs(0); setSwitched(false); setSelectedSide(null); }}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill={col.primary}><path d="M6 6h12v12H6z"/></svg>
              <span style={{ ...T.titleSm, color: col.primary }}>Stop Session</span>
            </OutlinedCardBtn>
          </>
        )}
      </div>
    </div>
  );
}

function SleepScreen({ onNav, dark }) {
  const col = dark ? AKDark : AK;
  const [phase, setPhase] = React.useState('idle'); // idle | active
  const [elapsed, setElapsed] = React.useState(0);

  React.useEffect(() => {
    if (phase !== 'active') return;
    const iv = setInterval(() => setElapsed(e => e + 1), 1000);
    return () => clearInterval(iv);
  }, [phase]);

  const fmt = s => {
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  };

  const entries = [
    { type: 'NAP', emoji: '🌞', color: col.primaryContainer, title: 'Nap', sub: '10:00 AM – 11:30 AM', dur: '1h 30m' },
    { type: 'NIGHT', emoji: '🌙', color: col.secondaryContainer, title: 'Night Sleep', sub: '9:00 PM – 5:30 AM', dur: '7h 30m' },
  ];

  return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <TopBar title="Sleep" onBack={() => onNav('home')} onAction={() => {}} actionLabel="History" dark={dark} />
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 16px 16px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        {/* Wake time chip */}
        <div style={{ background: col.secondaryContainer, borderRadius: R.xl, padding: '10px 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ ...T.bodyMd, color: col.onSecondaryContainer, fontWeight: 500 }}>🌅 &nbsp;Woke at 7:00 AM</div>
          <div style={{ ...T.bodySm, color: col.onSecondaryContainer }}>✏</div>
        </div>
        {/* Summary */}
        <div style={{ background: `${col.secondaryContainer}80`, borderRadius: R.md, padding: 12, display: 'flex', justifyContent: 'space-around' }}>
          {[['9h 0m', 'Sleep today'], ['2', 'Naps'], ['7h 30m', 'Night sleep']].map(([v, l]) => (
            <div key={l} style={{ textAlign: 'center' }}>
              <div style={{ ...T.titleMd, color: col.secondary, fontWeight: 700 }}>{v}</div>
              <div style={{ ...T.labelSm, color: col.onSurfaceVariant }}>{l}</div>
            </div>
          ))}
        </div>
        {/* Section */}
        <SectionLabel color={col.secondary}>Today</SectionLabel>
        {/* Active session */}
        {phase === 'active' && (
          <div style={{ background: col.secondary, borderRadius: R.lg, padding: 16, display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 20 }}>🌙</span>
            <div style={{ flex: 1 }}>
              <div style={{ ...T.titleSm, color: '#fff', fontWeight: 700 }}>Sleep in progress</div>
              <div style={{ ...T.bodySm, color: 'rgba(255,255,255,0.8)' }}>{fmt(elapsed)} elapsed</div>
            </div>
          <div onClick={() => { setPhase('idle'); setElapsed(0); }} style={{ border: '1.5px solid #fff', borderRadius: R.xl, padding: '6px 14px', color: '#fff', fontSize: 12, fontWeight: 600, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6 }}>
              <svg width="14" height="14" viewBox="0 0 24 24" fill="#fff"><path d="M6 6h12v12H6z"/></svg>
              Stop
            </div>
          </div>
        )}
        {/* History entries */}
        {entries.map(e => (
          <HistoryCard key={e.type} emoji={e.emoji} badgeColor={e.color} title={e.title} subtitle={e.sub} trailing={e.dur} trailColor={col.secondary} />
        ))}
        {/* Add entry button */}
        <PillBtn color={col.secondary} style={{ marginTop: 4 }} onClick={() => setPhase('active')}>
          + {phase === 'active' ? 'Sleep Running…' : 'Start Sleep / Add Entry'}
        </PillBtn>
        {/* History + Schedule */}
        <div style={{ display: 'flex', gap: 10 }}>
          <div style={{ flex: 1, border: `1.5px solid ${col.outline}`, borderRadius: R.xl, padding: '11px', textAlign: 'center', ...T.titleSm, color: col.onSurface, cursor: 'pointer' }}>History</div>
          <div style={{ flex: 1, border: `1.5px solid ${col.outline}`, borderRadius: R.xl, padding: '11px', textAlign: 'center', ...T.titleSm, color: col.onSurface, cursor: 'pointer' }}>Schedule</div>
        </div>
      </div>
    </div>
  );
}

function SettingsScreen({ onNav, dark, onToggleDark }) {
  const col = dark ? AKDark : AK;
  const baby = { name: 'Mia', dob: 'Jan 14, 2025 (14w old)', allergies: ['Dairy', 'Soy'] };

  const row = (label, value, action = 'Edit') => (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px' }}>
        <div>
          <div style={{ ...T.bodyLg, color: col.onSurface }}>{label}</div>
          <div style={{ ...T.bodyMd, color: col.onSurfaceVariant }}>{value}</div>
        </div>
        <div style={{ ...T.titleSm, color: col.primary, cursor: 'pointer' }}>{action}</div>
      </div>
      <Divider />
    </div>
  );

  return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <TopBar title="Settings" onBack={() => onNav('home')} dark={dark} />
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {/* Baby Profile */}
        <div style={{ padding: '12px 16px 4px' }}><SectionLabel>Baby Profile</SectionLabel></div>
        {row('Name', baby.name)}
        {row('Date of birth', baby.dob)}
        {/* Allergies row */}
        <div style={{ padding: '12px 16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div style={{ ...T.bodyLg, color: col.onSurface }}>Allergies</div>
            <div style={{ ...T.titleSm, color: col.primary, cursor: 'pointer' }}>Edit</div>
          </div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
            {baby.allergies.map(a => (
              <span key={a} style={{ background: col.secondaryContainer, color: col.onSecondaryContainer, borderRadius: R.xl, padding: '4px 12px', fontSize: 12, fontWeight: 600 }}>{a}</span>
            ))}
          </div>
        </div>
        <Divider />

        {/* Feeding Limits */}
        <div style={{ padding: '12px 16px 4px' }}><SectionLabel>Feeding Limits</SectionLabel></div>
        {row('Max per breast', '10 min')}
        {row('Max total feed', '20 min')}

        {/* App Settings */}
        <div style={{ padding: '12px 16px 4px' }}><SectionLabel color={col.onSurfaceVariant}>App Settings</SectionLabel></div>
        {row('Theme', dark ? 'Dark' : 'Light')}
        {/* Dark mode toggle */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px' }}>
          <div>
            <div style={{ ...T.bodyLg, color: col.onSurface }}>Dark Mode</div>
            <div style={{ ...T.bodyMd, color: col.onSurfaceVariant }}>Toggle for preview</div>
          </div>
          <div onClick={onToggleDark} style={{
            width: 48, height: 28, borderRadius: 14, background: dark ? col.primary : col.outline, cursor: 'pointer', position: 'relative', transition: 'background 0.2s',
          }}>
            <div style={{ position: 'absolute', top: 4, left: dark ? 24 : 4, width: 20, height: 20, borderRadius: '50%', background: '#fff', transition: 'left 0.2s' }} />
          </div>
        </div>
        <Divider />
        <div style={{ padding: '32px 16px 16px', ...T.bodySm, color: col.onSurfaceVariant }}>Akachan v1.0.0</div>
      </div>
    </div>
  );
}

function OnboardingScreen({ onComplete, dark }) {
  const col = dark ? AKDark : AK;
  const [step, setStep] = React.useState(0); // 0=welcome 1=baby-info 2=allergies
  const [name, setName] = React.useState('');

  const allergies = ['Dairy', 'Eggs', 'Gluten', 'Nuts', 'Soy', 'Fish'];
  const [selected, setSelected] = React.useState(new Set());

  if (step === 0) return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: 32, gap: 24 }}>
      <div style={{ width: 80, height: 80, borderRadius: '50%', background: AK.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 36 }}>🍼</div>
      <div style={{ ...T.headlineLg, color: col.onSurface, textAlign: 'center' }}>Welcome to Akachan</div>
      <div style={{ ...T.bodyLg, color: col.onSurfaceVariant, textAlign: 'center', lineHeight: '24px' }}>Your gentle baby tracker for feeding, sleep, and growth.</div>
      <PillBtn onClick={() => setStep(1)} style={{ width: '100%', marginTop: 8 }}>Get Started</PillBtn>
    </div>
  );

  if (step === 1) return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column', padding: 24, gap: 20 }}>
      <div style={{ ...T.titleLg, color: col.onSurface }}>Tell us about your baby</div>
      <div>
        <div style={{ ...T.labelMd, color: col.onSurfaceVariant, marginBottom: 6 }}>Baby's Name</div>
        <input value={name} onChange={e => setName(e.target.value)} placeholder="e.g. Mia" style={{
          width: '100%', padding: '14px 16px', borderRadius: R.md, border: `1.5px solid ${name ? AK.primary : col.outline}`,
          fontSize: 16, fontFamily: 'Roboto, sans-serif', outline: 'none', background: col.surface, color: col.onSurface, boxSizing: 'border-box',
        }} />
      </div>
      <div>
        <div style={{ ...T.labelMd, color: col.onSurfaceVariant, marginBottom: 6 }}>Date of Birth</div>
        <div style={{ padding: '14px 16px', borderRadius: R.md, border: `1.5px solid ${col.outline}`, ...T.bodyLg, color: col.onSurfaceVariant }}>Jan 14, 2025</div>
      </div>
      <div style={{ flex: 1 }} />
      <div style={{ display: 'flex', gap: 12 }}>
        <div onClick={() => setStep(0)} style={{ flex: 1, border: `1.5px solid ${col.outline}`, borderRadius: R.xl, padding: '12px', textAlign: 'center', ...T.titleSm, color: col.onSurface, cursor: 'pointer' }}>Back</div>
        <PillBtn onClick={() => setStep(2)} style={{ flex: 2 }} color={name ? AK.primary : '#CCC'}>Next</PillBtn>
      </div>
    </div>
  );

  return (
    <div style={{ background: col.surface, height: '100%', display: 'flex', flexDirection: 'column', padding: 24, gap: 20 }}>
      <div style={{ ...T.titleLg, color: col.onSurface }}>Any allergies to track?</div>
      <div style={{ ...T.bodyMd, color: col.onSurfaceVariant }}>Select all that apply for {name || 'your baby'}. You can change this later.</div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {allergies.map(a => {
          const sel = selected.has(a);
          return (
            <div key={a} onClick={() => setSelected(s => { const n = new Set(s); sel ? n.delete(a) : n.add(a); return n; })}
              style={{ background: sel ? AK.primaryContainer : col.surface, color: sel ? AK.onPrimaryContainer : col.onSurface, border: `1.5px solid ${sel ? AK.primary : col.outline}`, borderRadius: R.xl, padding: '8px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
              {sel ? '✓ ' : ''}{a}
            </div>
          );
        })}
      </div>
      <div style={{ flex: 1 }} />
      <div style={{ display: 'flex', gap: 12 }}>
        <div onClick={() => setStep(1)} style={{ flex: 1, border: `1.5px solid ${col.outline}`, borderRadius: R.xl, padding: '12px', textAlign: 'center', ...T.titleSm, color: col.onSurface, cursor: 'pointer' }}>Back</div>
        <PillBtn onClick={onComplete} style={{ flex: 2 }}>Finish Setup</PillBtn>
      </div>
    </div>
  );
}

Object.assign(window, {
  AK, AKDark, R, T,
  Card, OutlinedCardBtn, PillBtn, SectionLabel, Divider, TopBar,
  HistoryCard, RingTimer, SideSelector, BottomNav,
  HomeScreen, BreastfeedingScreen, SleepScreen, SettingsScreen, OnboardingScreen,
});

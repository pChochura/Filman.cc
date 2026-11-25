import React, { useEffect, useState } from 'react';
import { t } from '../i18n/index.js';
import { TmdbFeed, fetchFeedPage } from '../data/api/tmdbClient.js';
import { useSession } from '../data/local/sessionStore.js';
import { loginAccount, registerAccount } from '../data/sync/syncManager.js';
import { checkDevicePairCode, authorizeDevicePair } from '../data/api/deviceApiClient.js';
import TiwiButton from '../components/TiwiButton.jsx';
import TabRowSection from '../components/sections/TabRowSection.jsx';
import { IconLogin, IconTv } from '../components/Icons.jsx';

export default function PairDeviceScreen({ initialCode, onSuccess, onBack, onNavigate }) {
  const session = useSession();
  const [code, setCode] = useState(() => (initialCode ? String(initialCode).trim().toUpperCase() : ''));
  const [deviceInfo, setDeviceInfo] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isAuthorizing, setIsAuthorizing] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [isSuccess, setIsSuccess] = useState(false);

  // Login form state for unauthenticated users
  const [tab, setTab] = useState(0); // 0 = login, 1 = register
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoggingIn, setIsLoggingIn] = useState(false);
  const [loginError, setLoginError] = useState(null);

  // Backdrop carousel
  const [backdrops, setBackdrops] = useState([]);
  const [slideIdx, setSlideIdx] = useState(0);

  useEffect(() => {
    fetchFeedPage(TmdbFeed.TRENDING_ALL_DAY, 1).then((res) => {
      const urls = (res.featuredItems || []).map((m) => m.backgroundUrl).filter(Boolean);
      setBackdrops(urls);
    });
  }, []);

  useEffect(() => {
    if (backdrops.length <= 1) return undefined;
    const timer = setInterval(() => {
      setSlideIdx((prev) => (prev + 1) % backdrops.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [backdrops.length]);

  // Auto-verify code on mount if initialCode was provided
  useEffect(() => {
    if (initialCode && !deviceInfo && !isLoading) {
      handleCheck(initialCode);
    }
  }, [initialCode]);

  useEffect(() => {
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onBack?.();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onBack]);

  const handleCheck = async (codeToCheck) => {
    const target = String(codeToCheck || code).trim().toUpperCase();
    if (!target) {
      setErrorMessage(t('pair_screen_error_invalid'));
      return;
    }

    setIsLoading(true);
    setErrorMessage(null);
    try {
      const res = await checkDevicePairCode(target);
      if (res.valid) {
        setDeviceInfo(res);
        setCode(res.userCode || target);
      } else {
        setErrorMessage(t('pair_screen_error_invalid'));
      }
    } catch (err) {
      setErrorMessage(err?.message || t('pair_screen_error_invalid'));
    } finally {
      setIsLoading(false);
    }
  };

  const handleAuthorize = async () => {
    const target = code || deviceInfo?.userCode;
    if (!target) return;
    setIsAuthorizing(true);
    setErrorMessage(null);

    try {
      await authorizeDevicePair(target);
      setIsSuccess(true);
      onSuccess?.();
    } catch (err) {
      setErrorMessage(err?.message || t('pair_screen_error_invalid'));
    } finally {
      setIsAuthorizing(false);
    }
  };

  const handleLoginSubmit = async (e) => {
    e?.preventDefault?.();
    const u = username.trim();
    const p = password.trim();
    if (!u || !p) {
      setLoginError(t('login_error_invalid_credentials'));
      return;
    }

    setIsLoggingIn(true);
    setLoginError(null);
    try {
      if (tab === 0) {
        await loginAccount(u, p);
      } else {
        await registerAccount(u, p);
      }
      // If code was present, ensure it is validated
      if (code && !deviceInfo) {
        handleCheck(code);
      }
    } catch (err) {
      setLoginError(err?.message || t('login_error_server'));
    } finally {
      setIsLoggingIn(false);
    }
  };

  const isLoggedIn = Boolean(session?.authToken);
  const currentBackdrop = backdrops[slideIdx] || null;

  return (
    <div className="login-screen">
      {currentBackdrop && (
        <img
          key={currentBackdrop}
          src={currentBackdrop}
          alt=""
          className="login-backdrop-img"
        />
      )}
      <div className="hero-scrim" />

      {isSuccess ? (
        <div className="login-card" style={{ textAlign: 'center' }}>
          <div
            style={{
              width: '64px',
              height: '64px',
              borderRadius: '50%',
              background: 'var(--color-primary-container)',
              color: 'var(--color-on-primary-container)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '32px',
              margin: '0 auto',
            }}
          >
            ✓
          </div>
          <h1 className="type-headline-medium">{t('pair_screen_success_title')}</h1>
          <p className="type-body-medium" style={{ color: 'var(--color-on-surface-variant)' }}>
            {t('pair_screen_success_desc')}
          </p>
          <TiwiButton
            text={t('pair_screen_btn_done')}
            fullWidth={true}
            onClick={() => onNavigate?.({ name: 'home' })}
          />
        </div>
      ) : !isLoggedIn ? (
        <form className="login-card" onSubmit={handleLoginSubmit}>
          <h1 className="type-headline-medium">{t('app_name')}</h1>
          <p className="type-body-medium" style={{ color: 'var(--color-on-surface-variant)' }}>
            {code ? t('pair_screen_login_prompt') : t('pair_screen_login_required')}
          </p>

          <TabRowSection
            className="login-tabs"
            tabs={[
              { id: 0, label: t('login_login_tab') },
              { id: 1, label: t('login_register_tab') },
            ]}
            selectedId={tab}
            onSelectTab={(id) => {
              setTab(id);
              setLoginError(null);
            }}
          />

          {loginError && (
            <div className="login-error-banner type-body-small" role="alert">
              {loginError}
            </div>
          )}

          <input
            type="text"
            value={username}
            placeholder={t('login_username')}
            className="login-input"
            autoComplete="username"
            data-focusable="true"
            onChange={(e) => setUsername(e.target.value)}
          />

          <input
            type="password"
            value={password}
            placeholder={t('login_password')}
            className="login-input"
            autoComplete={tab === 0 ? 'current-password' : 'new-password'}
            data-focusable="true"
            onChange={(e) => setPassword(e.target.value)}
          />

          <TiwiButton
            text={tab === 0 ? t('login_confirm') : t('login_register_confirm')}
            icon={<IconLogin size={20} />}
            fullWidth={true}
            isLoading={isLoggingIn}
            onClick={handleLoginSubmit}
          />
        </form>
      ) : deviceInfo ? (
        <form
          className="login-card"
          onSubmit={(e) => {
            e.preventDefault();
            handleAuthorize();
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-sm)' }}>
            <IconTv size={28} />
            <h1 className="type-headline-medium">{t('pair_screen_title')}</h1>
          </div>

          <p className="type-body-medium" style={{ color: 'var(--color-on-surface-variant)' }}>
            {t('pair_screen_account_prompt', session.username || 'user')}
          </p>

          <div
            style={{
              background: 'var(--color-background)',
              padding: 'var(--spacing-md)',
              borderRadius: 'var(--shape-small)',
              border: '1px solid var(--color-surface-variant)',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: '4px',
            }}
          >
            <span className="type-label-small" style={{ color: 'var(--color-on-surface-variant)' }}>
              {deviceInfo.clientName || 'Android TV'}
            </span>
            <span
              className="type-headline-medium"
              style={{ letterSpacing: '4px', fontWeight: 'bold' }}
            >
              {deviceInfo.userCode || code}
            </span>
          </div>

          {errorMessage && (
            <div className="login-error-banner type-body-small" role="alert">
              {errorMessage}
            </div>
          )}

          <TiwiButton
            text={t('pair_screen_btn_authorize')}
            icon={<IconLogin size={20} />}
            fullWidth={true}
            isLoading={isAuthorizing}
            onClick={handleAuthorize}
          />
        </form>
      ) : (
        <form
          className="login-card"
          onSubmit={(e) => {
            e.preventDefault();
            handleCheck();
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--spacing-sm)' }}>
            <IconTv size={28} />
            <h1 className="type-headline-medium">{t('pair_screen_title')}</h1>
          </div>

          <p className="type-body-medium" style={{ color: 'var(--color-on-surface-variant)' }}>
            {t('pair_screen_description')}
          </p>

          {errorMessage && (
            <div className="login-error-banner type-body-small" role="alert">
              {errorMessage}
            </div>
          )}

          <input
            type="text"
            value={code}
            placeholder={t('pair_screen_code_placeholder')}
            className="login-input"
            autoComplete="off"
            autoFocus
            maxLength={10}
            data-focusable="true"
            onChange={(e) => setCode(e.target.value.toUpperCase())}
          />

          <TiwiButton
            text={t('pair_screen_btn_check')}
            fullWidth={true}
            isLoading={isLoading}
            onClick={() => handleCheck()}
          />
        </form>
      )}
    </div>
  );
}
